package dev.mustafa.issues;

import static dev.mustafa.issues.Models.*;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class ApiController {
  private final IssueService service;
  private final JdbcTemplate db;

  @Value("${app.demo.enabled}")
  private boolean demo;

  ApiController(IssueService service, JdbcTemplate db) {
    this.service = service;
    this.db = db;
  }

  @GetMapping("/session")
  Map<String, Object> session(Authentication authentication, CsrfToken token) {
    var result = new LinkedHashMap<String, Object>();
    result.put("csrf", Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    AppUser user =
        authentication != null && authentication.getPrincipal() instanceof AppUser u ? u : null;
    result.put(
        "user",
        user == null
            ? null
            : new Identity(user.id(), user.username(), user.displayName(), user.role()));
    result.put("demo", demo);
    return result;
  }

  @GetMapping("/health")
  Map<String, String> health() {
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "UP");
  }

  @GetMapping("/summary")
  Summary summary(@AuthenticationPrincipal AppUser user) {
    return service.summary(user);
  }

  @GetMapping("/assignees")
  List<Person> assignees(@AuthenticationPrincipal AppUser user) {
    return service.assignees(user);
  }

  @GetMapping("/issues")
  Page list(
      @AuthenticationPrincipal AppUser user,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Priority priority,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(user, q, status, priority, page);
  }

  @GetMapping("/issues/{id}")
  Detail detail(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
    return service.detail(id, user);
  }

  @PostMapping("/issues")
  @ResponseStatus(HttpStatus.CREATED)
  Issue create(@Valid @RequestBody CreateIssue input, @AuthenticationPrincipal AppUser user) {
    return service.create(input, user);
  }

  @PatchMapping("/issues/{id}")
  Issue update(
      @PathVariable long id,
      @Valid @RequestBody UpdateIssue input,
      @AuthenticationPrincipal AppUser user) {
    return service.update(id, input, user);
  }

  @PostMapping("/issues/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, String> comment(
      @PathVariable long id,
      @Valid @RequestBody NewComment input,
      @AuthenticationPrincipal AppUser user) {
    service.comment(id, input, user);
    return Map.of("status", "created");
  }
}
