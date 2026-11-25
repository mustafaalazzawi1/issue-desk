package dev.mustafa.issues;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoData implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  @Value("${app.demo.password}")
  private String password;

  DemoData(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.queryForObject("SELECT pg_advisory_xact_lock(7112026)", (r, n) -> 0);
    if (db.queryForObject("SELECT COUNT(*) FROM app_users", Long.class) != 0) return;
    String hash = encoder.encode(password);
    for (String[] person :
        List.of(
            new String[] {"alex", "Alex Morgan", "MEMBER"},
            new String[] {"jamie", "Jamie Chen", "MEMBER"},
            new String[] {"sam", "Sam Rivera", "ADMIN"},
            new String[] {"riley", "Riley Brooks", "ADMIN"}))
      db.update(
          "INSERT INTO app_users(username,display_name,password_hash,role) VALUES(?,?,?,?)",
          person[0],
          person[1],
          hash,
          person[2]);
    long alex = id("alex"), jamie = id("jamie"), sam = id("sam"), riley = id("riley");
    seed(
        "VPN disconnects during video calls",
        "The VPN connection drops after about ten minutes on a video call. Reconnecting restores"
            + " access, but the issue repeats. Sample device: Demo Laptop 04.",
        "NETWORK",
        "HIGH",
        "IN_PROGRESS",
        alex,
        sam,
        18);
    seed(
        "Access request for the design workspace",
        "Please add my demo account to the design workspace so I can review the onboarding screens."
            + " Approval is ready from the sample project lead.",
        "ACCESS",
        "MEDIUM",
        "OPEN",
        alex,
        null,
        4);
    seed(
        "Shared printer reports an offline status",
        "The third-floor sample printer is marked offline. Other devices on the demonstration"
            + " network are available.",
        "DEVICE",
        "LOW",
        "OPEN",
        jamie,
        null,
        7);
    seed(
        "Invoice export returns an empty file",
        "Exporting the example monthly report produces a file without rows. The dashboard shows 24"
            + " sample records before export.",
        "APPLICATION",
        "URGENT",
        "OPEN",
        jamie,
        riley,
        2);
    seed(
        "Laptop camera is not detected",
        "The camera is unavailable in the sample meeting application. Reinstalling the device"
            + " driver resolved the reported issue.",
        "DEVICE",
        "MEDIUM",
        "RESOLVED",
        alex,
        sam,
        48);
    seed(
        "Password reset link expired",
        "A reset link for the fictional staff portal expired before it was used. A new request"
            + " completed successfully.",
        "ACCESS",
        "LOW",
        "RESOLVED",
        jamie,
        riley,
        72);
    seed(
        "Search results load slowly",
        "Searching the demo knowledge base takes several seconds. The issue is reproducible with"
            + " the sample account and a small query.",
        "APPLICATION",
        "MEDIUM",
        "IN_PROGRESS",
        alex,
        riley,
        25);
    seed(
        "Guest Wi-Fi unavailable in meeting room",
        "The fictional meeting-room network is visible but does not assign an address. The wired"
            + " demo connection works.",
        "NETWORK",
        "HIGH",
        "OPEN",
        jamie,
        null,
        11);
    long vpn =
        db.queryForObject(
            "SELECT id FROM issues WHERE title='VPN disconnects during video calls'", Long.class);
    db.update(
        "INSERT INTO comments(issue_id,author_id,body,created_at)"
            + " VALUES(?,?,?,CURRENT_TIMESTAMP-INTERVAL '2 hours')",
        vpn,
        sam,
        "I reproduced the disconnect on the sample device. Checking the network profile next.");
    db.update(
        "INSERT INTO audit_events(issue_id,actor_id,action,detail,created_at)"
            + " VALUES(?,?,'COMMENTED','Added a comment.',CURRENT_TIMESTAMP-INTERVAL '2 hours')",
        vpn,
        sam);
  }

  private long id(String name) {
    return db.queryForObject("SELECT id FROM app_users WHERE username=?", Long.class, name);
  }

  private void seed(
      String title,
      String description,
      String category,
      String priority,
      String status,
      long owner,
      Long assignee,
      int hours) {
    long issue =
        db.queryForObject(
            "INSERT INTO"
                + " issues(title,description,category,priority,status,owner_id,assignee_id,created_at,updated_at)"
                + " VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP-(? * INTERVAL '1"
                + " hour'),CURRENT_TIMESTAMP-(? * INTERVAL '1 hour')) RETURNING id",
            Long.class,
            title,
            description,
            category,
            priority,
            status,
            owner,
            assignee,
            hours,
            hours);
    db.update(
        "INSERT INTO audit_events(issue_id,actor_id,action,detail,created_at)"
            + " VALUES(?,?,'CREATED','Created this sample issue.',CURRENT_TIMESTAMP-(? * INTERVAL"
            + " '1 hour'))",
        issue,
        owner,
        hours);
  }
}
