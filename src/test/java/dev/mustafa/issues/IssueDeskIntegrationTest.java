package dev.mustafa.issues;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real HTTP requests, real sessions, and a fresh real PostgreSQL database. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.demo.enabled=true",
      "app.demo.rate-limit.enabled=false",
      "server.servlet.session.cookie.secure=false"
    })
class IssueDeskIntegrationTest {
  static final ObjectMapper JSON = new ObjectMapper();
  static final EmbeddedPostgres PG = startPostgres();
  @LocalServerPort int port;

  static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder()
          .setServerConfig("unix_socket_directories", "")
          .setPort(0)
          .start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "");
  }

  record Reply(int status, JsonNode json, HttpResponse<String> response) {}

  class Client {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrf, header;

    Reply send(String method, String path, String body, String contentType, boolean token)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(15));
      if (contentType != null) request.header("Content-Type", contentType);
      if (token && csrf != null) request.header(header, csrf);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body));
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode json;
      try {
        json = JSON.readTree(response.body());
      } catch (Exception ignored) {
        json = JSON.nullNode();
      }
      return new Reply(response.statusCode(), json, response);
    }

    Reply get(String path) throws Exception {
      return send("GET", path, null, null, false);
    }

    Reply write(String method, String path, Object body) throws Exception {
      return send(method, path, JSON.writeValueAsString(body), "application/json", true);
    }

    Reply session() throws Exception {
      var result = get("/api/session");
      assertEquals(200, result.status);
      csrf = result.json.at("/csrf/token").asText();
      header = result.json.at("/csrf/headerName").asText();
      return result;
    }

    Client login(String name) throws Exception {
      session();
      var result =
          send(
              "POST",
              "/api/login",
              "username="
                  + name
                  + "&password="
                  + URLEncoder.encode("IssueDesk-demo-2026!", StandardCharsets.UTF_8),
              "application/x-www-form-urlencoded",
              true);
      assertEquals(200, result.status, result.response.body());
      session();
      return this;
    }

    JsonNode create(String title) throws Exception {
      var result =
          write(
              "POST",
              "/api/issues",
              Map.of(
                  "title",
                  title,
                  "description",
                  "Fictional details for an integration test.",
                  "category",
                  "APPLICATION",
                  "priority",
                  "MEDIUM"));
      assertEquals(201, result.status, result.response.body());
      return result.json;
    }
  }

  Map<String, Object> change(JsonNode issue, String status, String priority, Long assignee) {
    var input = new LinkedHashMap<String, Object>();
    input.put("version", issue.get("version").asInt());
    input.put("status", status);
    input.put("priority", priority);
    input.put("assigneeId", assignee);
    return input;
  }

  @Test
  void anonymousCannotReadPrivateEndpoints() throws Exception {
    var client = new Client();
    for (String path : List.of("/api/issues", "/api/summary", "/api/issues/1", "/api/assignees"))
      assertEquals(401, client.get(path).status, path);
    assertEquals(200, client.get("/api/health").status);
  }

  @Test
  void signInRotatesSessionAndLogoutInvalidatesIt() throws Exception {
    var c = new Client();
    c.session();
    String before =
        c.cookies.getCookieStore().getCookies().stream()
            .filter(k -> k.getName().equals("JSESSIONID"))
            .findFirst()
            .orElseThrow()
            .getValue();
    c.login("alex");
    String after =
        c.cookies.getCookieStore().getCookies().stream()
            .filter(k -> k.getName().equals("JSESSIONID"))
            .findFirst()
            .orElseThrow()
            .getValue();
    assertNotEquals(before, after);
    assertEquals("MEMBER", c.session().json.at("/user/role").asText());
    assertEquals(204, c.send("POST", "/api/logout", null, null, true).status);
    assertEquals(401, c.get("/api/issues").status);
  }

  @Test
  void invalidCredentialsDoNotAuthenticate() throws Exception {
    var c = new Client();
    c.session();
    assertEquals(
        401,
        c.send(
                "POST",
                "/api/login",
                "username=alex&password=wrong",
                "application/x-www-form-urlencoded",
                true)
            .status);
    assertTrue(c.session().json.get("user").isNull());
  }

  @Test
  void csrfRequiredForLoginAndEveryMutation() throws Exception {
    var c = new Client();
    c.session();
    assertEquals(
        403,
        c.send(
                "POST",
                "/api/login",
                "username=alex&password=wrong",
                "application/x-www-form-urlencoded",
                false)
            .status);
    c.login("sam");
    var issue = c.create("CSRF check");
    String id = issue.get("id").asText();
    for (String[] request :
        new String[][] {
          {"POST", "/api/issues"},
          {"PATCH", "/api/issues/" + id},
          {"POST", "/api/issues/" + id + "/comments"},
          {"POST", "/api/logout"}
        })
      assertEquals(403, c.send(request[0], request[1], "{}", "application/json", false).status);
  }

  @Test
  void membersOnlyListOwnIssuesAndHaveScopedCounts() throws Exception {
    var alex = new Client().login("alex");
    var jamie = new Client().login("jamie");
    var issue = alex.create("Ownership boundary");
    long alexId = alex.session().json.at("/user/id").asLong();
    var page = alex.get("/api/issues").json;
    for (var item : page.get("items")) assertEquals(alexId, item.at("/owner/id").asLong());
    assertEquals(page.get("total").asLong(), alex.get("/api/summary").json.get("total").asLong());
    assertEquals(404, jamie.get("/api/issues/" + issue.get("id")).status);
    assertEquals(404, jamie.get("/api/issues/9999999").status);
  }

  @Test
  void memberCannotCommentOnSomeoneElsesIssue() throws Exception {
    var alex = new Client().login("alex");
    var issue = alex.create("Private member issue");
    var jamie = new Client().login("jamie");
    assertEquals(
        404,
        jamie.write(
                "POST",
                "/api/issues/" + issue.get("id") + "/comments",
                Map.of("body", "Not my report"))
            .status);
    assertEquals(0, alex.get("/api/issues/" + issue.get("id")).json.get("comments").size());
  }

  @Test
  void membersCannotChangeStatusOrAssignment() throws Exception {
    var c = new Client().login("alex");
    var issue = c.create("Member cannot triage");
    assertEquals(
        403,
        c.write("PATCH", "/api/issues/" + issue.get("id"), change(issue, "RESOLVED", "HIGH", null))
            .status);
    assertEquals(403, c.get("/api/assignees").status);
    assertEquals("OPEN", c.get("/api/issues/" + issue.get("id")).json.at("/issue/status").asText());
  }

  @Test
  void administratorAssignsAndResolvesWithAudit() throws Exception {
    var member = new Client().login("alex");
    var issue = member.create("Admin workflow");
    var admin = new Client().login("sam");
    var people = admin.get("/api/assignees").json;
    assertEquals(2, people.size());
    long assignee = people.get(0).get("id").asLong();
    var result =
        admin.write(
            "PATCH", "/api/issues/" + issue.get("id"), change(issue, "RESOLVED", "HIGH", assignee));
    assertEquals(200, result.status);
    assertEquals(1, result.json.get("version").asInt());
    var detail = member.get("/api/issues/" + issue.get("id")).json;
    assertEquals("RESOLVED", detail.at("/issue/status").asText());
    assertEquals(assignee, detail.at("/issue/assignee/id").asLong());
    assertEquals(2, detail.get("activity").size());
    assertEquals("Sam Rivera", detail.get("activity").get(1).at("/actor/displayName").asText());
    assertTrue(detail.get("activity").get(1).get("detail").asText().contains("Assignment:"));
  }

  @Test
  void staleVersionReturnsConflictWithoutOverwritingOrAddingAudit() throws Exception {
    var admin = new Client().login("sam");
    var issue = admin.create("Conflict protection");
    String path = "/api/issues/" + issue.get("id");
    assertEquals(
        200, admin.write("PATCH", path, change(issue, "IN_PROGRESS", "HIGH", null)).status);
    assertEquals(409, admin.write("PATCH", path, change(issue, "RESOLVED", "LOW", null)).status);
    var detail = admin.get(path).json;
    assertEquals("IN_PROGRESS", detail.at("/issue/status").asText());
    assertEquals(2, detail.get("activity").size());
  }

  @Test
  void simultaneousUpdatesHaveExactlyOneWinner() throws Exception {
    var first = new Client().login("sam");
    var second = new Client().login("riley");
    var issue = first.create("Concurrent edits");
    String path = "/api/issues/" + issue.get("id");
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a =
          executor.submit(
              () -> {
                gate.await();
                return first.write("PATCH", path, change(issue, "RESOLVED", "HIGH", null)).status;
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return second.write("PATCH", path, change(issue, "IN_PROGRESS", "LOW", null))
                    .status;
              });
      gate.countDown();
      assertEquals(
          Set.of(200, 409), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
    }
    assertEquals(2, first.get(path).json.get("activity").size());
  }

  @Test
  void invalidAssigneeDoesNotPartiallyChangeIssue() throws Exception {
    var admin = new Client().login("sam");
    var member = new Client().login("alex");
    long memberId = member.session().json.at("/user/id").asLong();
    var issue = admin.create("Assignment validation");
    String path = "/api/issues/" + issue.get("id");
    assertEquals(
        400, admin.write("PATCH", path, change(issue, "RESOLVED", "URGENT", memberId)).status);
    assertEquals(
        400, admin.write("PATCH", path, change(issue, "RESOLVED", "URGENT", 999999L)).status);
    assertEquals("OPEN", admin.get(path).json.at("/issue/status").asText());
    assertEquals(1, admin.get(path).json.get("activity").size());
  }

  @Test
  void commentAndAuditPersistAcrossSessions() throws Exception {
    var member = new Client().login("alex");
    var issue = member.create("Persistent discussion");
    String path = "/api/issues/" + issue.get("id");
    assertEquals(
        201,
        member.write(
                "POST",
                path + "/comments",
                Map.of("body", "Sample update: reproduced on a test device."))
            .status);
    var fresh = new Client().login("alex");
    var detail = fresh.get(path).json;
    assertEquals(1, detail.get("comments").size());
    assertEquals("Alex Morgan", detail.get("comments").get(0).at("/author/displayName").asText());
    assertEquals("COMMENTED", detail.get("activity").get(1).get("action").asText());
  }

  @Test
  void filtersAndLiteralSearchDoNotBroadenResults() throws Exception {
    var c = new Client().login("alex");
    var issue = c.create("Literal 100%_search '+OR+1=1");
    var found =
        c.get("/api/issues?q=" + URLEncoder.encode("100%_search", StandardCharsets.UTF_8)).json;
    assertEquals(1, found.get("total").asInt());
    assertEquals(issue.get("id"), found.get("items").get(0).get("id"));
    assertEquals(
        0,
        c.get("/api/issues?q=" + URLEncoder.encode("' OR 1=1 --", StandardCharsets.UTF_8))
            .json
            .get("total")
            .asInt());
    for (var item : c.get("/api/issues?status=OPEN&priority=MEDIUM").json.get("items")) {
      assertEquals("OPEN", item.get("status").asText());
      assertEquals("MEDIUM", item.get("priority").asText());
    }
  }

  @Test
  void paginationIsBoundedAndRejectsBadInputs() throws Exception {
    var c = new Client().login("sam");
    for (int n = 0; n < 21; n++) c.create("Pagination fixture " + n);
    var first = c.get("/api/issues").json;
    var second = c.get("/api/issues?page=1").json;
    assertEquals(20, first.get("items").size());
    assertFalse(second.get("items").isEmpty());
    var ids = new HashSet<Long>();
    for (var item : first.get("items")) ids.add(item.get("id").asLong());
    for (var item : second.get("items")) assertFalse(ids.contains(item.get("id").asLong()));
    for (String query :
        List.of(
            "page=-1",
            "page=1001",
            "page=no",
            "status=INVALID",
            "priority=CRITICAL",
            "q=" + "x".repeat(121))) assertEquals(400, c.get("/api/issues?" + query).status, query);
  }

  @Test
  void malformedBlankAndOversizedFieldsRejected() throws Exception {
    var c = new Client().login("alex");
    assertEquals(
        400,
        c.write(
                "POST",
                "/api/issues",
                Map.of(
                    "title",
                    " ",
                    "description",
                    "short",
                    "category",
                    "APPLICATION",
                    "priority",
                    "HIGH"))
            .status);
    assertEquals(
        400,
        c.write(
                "POST",
                "/api/issues",
                Map.of(
                    "title",
                    "x".repeat(121),
                    "description",
                    "long enough description",
                    "category",
                    "APPLICATION",
                    "priority",
                    "HIGH"))
            .status);
    assertEquals(400, c.send("POST", "/api/issues", "{broken", "application/json", true).status);
    assertEquals(
        400,
        c.write(
                "POST",
                "/api/issues",
                Map.of(
                    "title",
                    "Invalid enum",
                    "description",
                    "long enough description",
                    "category",
                    "NOPE",
                    "priority",
                    "HIGH"))
            .status);
    var issue = c.create("Comment validation");
    for (String body : List.of("   ", "x".repeat(2001)))
      assertEquals(
          400,
          c.write("POST", "/api/issues/" + issue.get("id") + "/comments", Map.of("body", body))
              .status);
  }

  @Test
  void bodySizeLimitHandlesKnownAndChunkedLengths() throws Exception {
    var c = new Client().login("alex");
    String body = "{\"body\":\"" + "x".repeat(17000) + "\"}";
    for (String contentType :
        List.of(
            "application/json",
            "application/problem+json",
            "application/vnd.issue+json; charset=UTF-8")) {
      assertEquals(413, c.send("POST", "/api/issues", body, contentType, true).status, contentType);
      var req =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/issues"))
              .header("Content-Type", contentType)
              .header(c.header, c.csrf)
              .POST(
                  HttpRequest.BodyPublishers.ofInputStream(
                      () ->
                          new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))))
              .build();
      assertEquals(
          413, c.http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode(), contentType);
    }
  }

  @Test
  void clientCannotChooseOwnerRoleOrInitialStatus() throws Exception {
    var c = new Client().login("alex");
    var response =
        c.write(
            "POST",
            "/api/issues",
            Map.of(
                "title",
                "Mass assignment attempt",
                "description",
                "Fictional test input description",
                "category",
                "ACCESS",
                "priority",
                "LOW",
                "ownerId",
                999999,
                "status",
                "RESOLVED",
                "role",
                "ADMIN"));
    assertEquals(201, response.status);
    assertEquals("OPEN", response.json.get("status").asText());
    assertEquals(c.session().json.at("/user/id"), response.json.at("/owner/id"));
    assertEquals("MEMBER", c.session().json.at("/user/role").asText());
  }

  @Test
  void responsesHideHashesAndSetBrowserProtectionHeaders() throws Exception {
    var c = new Client().login("alex");
    var result = c.get("/api/session");
    assertFalse(result.response.body().contains("password"));
    assertFalse(result.response.body().contains("$2"));
    assertTrue(
        result
            .response
            .headers()
            .firstValue("Content-Security-Policy")
            .orElse("")
            .contains("frame-ancestors 'none'"));
    assertEquals(
        "nosniff", result.response.headers().firstValue("X-Content-Type-Options").orElse(""));
    assertTrue(
        result.response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    var fresh = new Client();
    var session = fresh.session();
    String cookie = session.response.headers().firstValue("Set-Cookie").orElse("");
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
  }

  @Test
  void noOpUpdateDoesNotInventActivity() throws Exception {
    var admin = new Client().login("sam");
    var issue = admin.create("No change to save");
    String path = "/api/issues/" + issue.get("id");
    assertEquals(200, admin.write("PATCH", path, change(issue, "OPEN", "MEDIUM", null)).status);
    var detail = admin.get(path).json;
    assertEquals(0, detail.at("/issue/version").asInt());
    assertEquals(1, detail.get("activity").size());
  }
}
