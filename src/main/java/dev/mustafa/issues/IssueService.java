package dev.mustafa.issues;

import static dev.mustafa.issues.Models.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class IssueService {
  private final JdbcTemplate db;

  IssueService(JdbcTemplate db) {
    this.db = db;
  }

  static final String SELECT =
      "SELECT i.*, o.display_name owner_name, a.display_name assignee_name FROM issues i JOIN"
          + " app_users o ON o.id=i.owner_id LEFT JOIN app_users a ON a.id=i.assignee_id ";

  private Issue row(ResultSet r, int n) throws SQLException {
    Long assignee = r.getObject("assignee_id", Long.class);
    return new Issue(
        r.getLong("id"),
        r.getString("title"),
        r.getString("description"),
        Category.valueOf(r.getString("category")),
        Priority.valueOf(r.getString("priority")),
        Status.valueOf(r.getString("status")),
        new Person(r.getLong("owner_id"), r.getString("owner_name")),
        assignee == null ? null : new Person(assignee, r.getString("assignee_name")),
        r.getInt("version"),
        r.getObject("created_at", OffsetDateTime.class),
        r.getObject("updated_at", OffsetDateTime.class));
  }

  Issue visible(long id, AppUser user) {
    String query = SELECT + "WHERE i.id=?" + (user.admin() ? "" : " AND i.owner_id=?");
    Object[] args = user.admin() ? new Object[] {id} : new Object[] {id, user.id()};
    return db.query(query, this::row, args).stream()
        .findFirst()
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Issue not found."));
  }

  Page list(AppUser user, String search, Status status, Priority priority, int page) {
    if (page < 0 || page > 1000 || search.length() > 120)
      throw error(HttpStatus.BAD_REQUEST, "Invalid search or page.");
    var args = new ArrayList<Object>();
    var where = new StringBuilder(" WHERE 1=1");
    if (!user.admin()) {
      where.append(" AND i.owner_id=?");
      args.add(user.id());
    }
    if (status != null) {
      where.append(" AND i.status=?");
      args.add(status.name());
    }
    if (priority != null) {
      where.append(" AND i.priority=?");
      args.add(priority.name());
    }
    if (!search.isBlank()) {
      where.append(" AND (i.title ILIKE ? ESCAPE '\\' OR i.description ILIKE ? ESCAPE '\\')");
      String pattern =
          "%" + search.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      args.add(pattern);
      args.add(pattern);
    }
    long total =
        db.queryForObject("SELECT COUNT(*) FROM issues i" + where, Long.class, args.toArray());
    args.add(20);
    args.add(page * 20);
    return new Page(
        db.query(
            SELECT + where + " ORDER BY i.updated_at DESC, i.id DESC LIMIT ? OFFSET ?",
            this::row,
            args.toArray()),
        total,
        page,
        20);
  }

  Summary summary(AppUser user) {
    String where = user.admin() ? "" : " WHERE owner_id=?";
    Object[] args = user.admin() ? new Object[] {} : new Object[] {user.id()};
    return db.queryForObject(
        "SELECT count(*) total, count(*) FILTER(WHERE status='OPEN') opened, count(*) FILTER(WHERE"
            + " status='IN_PROGRESS') progressing, count(*) FILTER(WHERE status='RESOLVED')"
            + " resolved, count(*) FILTER(WHERE priority='URGENT' AND status<>'RESOLVED') urgent"
            + " FROM issues"
            + where,
        (r, n) ->
            new Summary(
                r.getLong("total"),
                r.getLong("opened"),
                r.getLong("progressing"),
                r.getLong("resolved"),
                r.getLong("urgent")),
        args);
  }

  Detail detail(long id, AppUser user) {
    Issue issue = visible(id, user);
    var comments =
        db.query(
            "SELECT c.*,u.display_name FROM comments c JOIN app_users u ON u.id=c.author_id WHERE"
                + " issue_id=? ORDER BY c.id",
            (r, n) ->
                new Comment(
                    r.getLong("id"),
                    r.getString("body"),
                    new Person(r.getLong("author_id"), r.getString("display_name")),
                    r.getObject("created_at", OffsetDateTime.class)),
            id);
    var activity =
        db.query(
            "SELECT e.*,u.display_name FROM audit_events e JOIN app_users u ON u.id=e.actor_id"
                + " WHERE issue_id=? ORDER BY e.id",
            (r, n) ->
                new Activity(
                    r.getLong("id"),
                    r.getString("action"),
                    r.getString("detail"),
                    new Person(r.getLong("actor_id"), r.getString("display_name")),
                    r.getObject("created_at", OffsetDateTime.class)),
            id);
    return new Detail(issue, comments, activity);
  }

  @Transactional
  Issue create(CreateIssue input, AppUser user) {
    // Serialize capacity checks within the shared demonstration dataset.
    db.queryForObject("SELECT pg_advisory_xact_lock(7112026)", (r, n) -> 0);
    if (db.queryForObject("SELECT COUNT(*) FROM issues", Long.class) >= 500)
      throw error(HttpStatus.TOO_MANY_REQUESTS, "The demo has reached its 500-issue limit.");
    long id =
        db.queryForObject(
            "INSERT INTO issues(title,description,category,priority,owner_id) VALUES (?,?,?,?,?)"
                + " RETURNING id",
            Long.class,
            input.title().strip(),
            input.description().strip(),
            input.category().name(),
            input.priority().name(),
            user.id());
    event(id, user, "CREATED", "Created this issue.");
    return visible(id, user);
  }

  @Transactional
  Issue update(long id, UpdateIssue input, AppUser user) {
    if (!user.admin())
      throw error(
          HttpStatus.FORBIDDEN,
          "Only administrators can change issue status, priority, or assignment.");
    Issue old = visible(id, user);
    if (input.version() != old.version())
      throw error(
          HttpStatus.CONFLICT, "This issue changed since you opened it. Reload before saving.");
    if (input.assigneeId() != null
        && db.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE id=? AND role='ADMIN'",
                Long.class,
                input.assigneeId())
            != 1) throw error(HttpStatus.BAD_REQUEST, "Assign issues to an administrator.");
    var changes = new ArrayList<String>();
    if (input.status() != old.status())
      changes.add("Status: " + words(old.status()) + " → " + words(input.status()));
    if (input.priority() != old.priority())
      changes.add("Priority: " + words(old.priority()) + " → " + words(input.priority()));
    Long previous = old.assignee() == null ? null : old.assignee().id();
    if (!Objects.equals(previous, input.assigneeId())) {
      String after =
          input.assigneeId() == null
              ? "Unassigned"
              : db.queryForObject(
                  "SELECT display_name FROM app_users WHERE id=?",
                  String.class,
                  input.assigneeId());
      changes.add(
          "Assignment: "
              + (old.assignee() == null ? "Unassigned" : old.assignee().displayName())
              + " → "
              + after);
    }
    if (changes.isEmpty()) return old;
    int changed =
        db.update(
            "UPDATE issues SET"
                + " status=?,priority=?,assignee_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                + " WHERE id=? AND version=?",
            input.status().name(),
            input.priority().name(),
            input.assigneeId(),
            id,
            input.version());
    if (changed != 1)
      throw error(HttpStatus.CONFLICT, "Another update was saved first. Reload this issue.");
    event(id, user, "UPDATED", String.join("; ", changes) + ".");
    return visible(id, user);
  }

  @Transactional
  void comment(long id, NewComment input, AppUser user) {
    visible(id, user);
    db.queryForObject("SELECT id FROM issues WHERE id=? FOR UPDATE", Long.class, id);
    if (db.queryForObject("SELECT COUNT(*) FROM comments WHERE issue_id=?", Long.class, id) >= 100)
      throw error(
          HttpStatus.TOO_MANY_REQUESTS, "This demo issue has reached its 100-comment limit.");
    db.update(
        "INSERT INTO comments(issue_id,author_id,body) VALUES(?,?,?)",
        id,
        user.id(),
        input.body().strip());
    db.update("UPDATE issues SET updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    event(id, user, "COMMENTED", "Added a comment.");
  }

  List<Person> assignees(AppUser user) {
    if (!user.admin())
      throw error(HttpStatus.FORBIDDEN, "Only administrators can view assignment options.");
    return db.query(
        "SELECT id,display_name FROM app_users WHERE role='ADMIN' ORDER BY display_name",
        (r, n) -> new Person(r.getLong("id"), r.getString("display_name")));
  }

  private void event(long id, AppUser user, String action, String detail) {
    db.update(
        "INSERT INTO audit_events(issue_id,actor_id,action,detail) VALUES(?,?,?,?)",
        id,
        user.id(),
        action,
        detail);
  }

  private String words(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
