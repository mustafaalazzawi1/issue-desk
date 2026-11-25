package dev.mustafa.issues;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.demo.enabled=true",
      "app.demo.rate-limit.requests-per-minute=8",
      "app.demo.rate-limit.logins-per-minute=3",
      "server.servlet.session.cookie.secure=false"
    })
class DemoRateLimitHttpTest {
  @LocalServerPort int port;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> IssueDeskIntegrationTest.PG.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "");
  }

  @Test
  void liveHttpPolicyLimitsLoginAndApiButPreservesHealthAndStaticPages() throws Exception {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    var session = client.send(get("/api/session"), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, session.statusCode());
    var csrf = new ObjectMapper().readTree(session.body()).get("csrf");
    for (int i = 0; i < 4; i++) {
      var login =
          HttpRequest.newBuilder(uri("/api/login"))
              .timeout(Duration.ofSeconds(10))
              .header(csrf.get("headerName").asText(), csrf.get("token").asText())
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString("username=alex&password=invalid"))
              .build();
      var response = client.send(login, HttpResponse.BodyHandlers.ofString());
      assertEquals(i < 3 ? 401 : 429, response.statusCode(), response.body());
      if (i == 3) assertTrue(response.body().contains("demo_rate_limit"));
    }
    for (int i = 0; i < 4; i++)
      assertEquals(
          200, client.send(get("/api/session"), HttpResponse.BodyHandlers.ofString()).statusCode());
    var limited =
        HttpClient.newHttpClient().send(get("/api/session"), HttpResponse.BodyHandlers.ofString());
    assertEquals(429, limited.statusCode());
    assertTrue(Integer.parseInt(limited.headers().firstValue("Retry-After").orElseThrow()) > 0);
    assertEquals("no-store", limited.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(
        200, client.send(get("/api/health"), HttpResponse.BodyHandlers.ofString()).statusCode());
    assertEquals(200, client.send(get("/"), HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private HttpRequest get(String path) {
    return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build();
  }
}
