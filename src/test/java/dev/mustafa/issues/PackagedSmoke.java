package dev.mustafa.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Verifies the actual production JAR against an isolated, real database. */
public class PackagedSmoke {
  public static void main(String[] args) throws Exception {
    try (var pg =
        EmbeddedPostgres.builder()
            .setServerConfig("unix_socket_directories", "")
            .setPort(0)
            .start()) {
      int port;
      try (var socket = new ServerSocket(0)) {
        port = socket.getLocalPort();
      }
      var builder =
          new ProcessBuilder(
              Path.of(System.getProperty("java.home"), "bin", "java").toString(),
              "-jar",
              "target/issue-tracker-0.1.1.jar");
      builder.environment().put("DATABASE_URL", pg.getJdbcUrl("postgres", "postgres"));
      builder.environment().put("DATABASE_USERNAME", "postgres");
      builder.environment().put("DATABASE_PASSWORD", "");
      builder.environment().put("DEMO_ENABLED", "true");
      builder.environment().put("COOKIE_SECURE", "false");
      builder.environment().put("PORT", Integer.toString(port));
      builder.redirectErrorStream(true).redirectOutput(Path.of("target/jar-smoke.log").toFile());
      var process = builder.start();
      try {
        var http =
            HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        String base = "http://localhost:" + port;
        boolean ready = false;
        for (int n = 0; n < 60 && process.isAlive(); n++) {
          try {
            var response =
                http.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
              ready = true;
              break;
            }
          } catch (Exception ignored) {
          }
          Thread.sleep(500);
        }
        if (!ready)
          throw new IllegalStateException("JAR did not become ready. See target/jar-smoke.log");
        for (String path : new String[] {"/", "/app.js", "/styles.css", "/icon.svg"}) {
          var response =
              http.send(
                  HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() != 200 || response.body().isBlank())
            throw new IllegalStateException("Missing asset: " + path);
        }
        var json = new ObjectMapper();
        var session =
            http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/session")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var token = json.readTree(session.body()).get("csrf");
        var login =
            HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .header(token.get("headerName").asText(), token.get("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=alex&password="
                            + URLEncoder.encode("IssueDesk-demo-2026!", StandardCharsets.UTF_8)))
                .build();
        if (http.send(login, HttpResponse.BodyHandlers.ofString()).statusCode() != 200)
          throw new IllegalStateException("JAR sign-in failed");
        var issues =
            http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/issues")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (issues.statusCode() != 200 || json.readTree(issues.body()).get("total").asInt() != 4)
          throw new IllegalStateException("Unexpected scoped demo dataset");
        System.out.println(
            "PASS packaged JAR: assets, database readiness, real sign-in, and scoped issues");
      } finally {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
      }
    }
  }
}
