# Live deployment

[Open Issue Desk](https://issue-desk-production.up.railway.app/)

Verified November 25, 2026. The public application runs the repository Dockerfile on Railway Free and uses a dedicated Neon Free PostgreSQL 17 database. Railway is pinned to a reviewed source commit rather than automatically following every push. The deployed revision and checks are recorded in [verification](VERIFICATION.md).

## Service configuration

- Dockerfile at repository root; one replica, port `8080`.
- Memory limit: 0.5 GB; CPU limit: 1 vCPU.
- Health check: `/api/health`, which queries the database.
- Application sleeping enabled; restart on failure, at most two retries.
- No application volume. Persistent reports live in the dedicated PostgreSQL database.
- `COOKIE_SECURE=true` and `DEMO_ENABLED=true`.
- `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` are service-specific secret variables. Their values are not stored in source control.

The JDBC URL uses `sslmode=verify-full`. The service also sets the following non-secret `SPRING_APPLICATION_JSON`:

```json
{
  "spring": {
    "datasource": {
      "hikari": {
        "minimum-idle": 0,
        "maximum-pool-size": 3,
        "idle-timeout": 60000,
        "keepalive-time": 0,
        "connection-timeout": 30000,
        "data-source-properties": {
          "sslfactory": "org.postgresql.ssl.DefaultJavaSSLFactory"
        }
      }
    }
  }
}
```

`DefaultJavaSSLFactory` uses the JRE trust store. This corrects the PostgreSQL driver's default expectation of a local `root.crt` file without disabling certificate or hostname verification. The small connection pool releases idle connections, allowing the database and application to sleep.

## Budget and availability

The deployment uses Railway Free with no payment method and Neon Free. Railway's monthly usage allowance is shared within the workspace. The service may sleep while idle, take time to wake, or stop when the allowance is exhausted. Do not upgrade plans, purchase credits, or enable paid overage as part of this demo. No continuous-availability guarantee is made.

The seeded accounts are intentionally public. Visitors share data; use fictional information only. The dataset is not automatically reset. The application limits total issues, comments per issue, and traffic in demo mode. Defaults are 240 API requests, 12 login attempts, and 30 other mutations per one-minute window, shared by all visitors. Request budgets run before authentication and request-body parsing. The limit response includes a wait message and `Retry-After`; static files, health, and logout are exempt. The budgets occupy constant memory and reset on restart. They are for one replica, not provider-level DDoS protection.

Optional Spring properties are `app.demo.rate-limit.enabled`, `app.demo.rate-limit.requests-per-minute`, `app.demo.rate-limit.logins-per-minute`, and `app.demo.rate-limit.writes-per-minute`. Each numeric limit must be positive. Keep the policy enabled on the public demo. Existing production environment variables and provider plans need no changes.

[Owner maintenance](DEMO-OPERATIONS.md) is read-only by default. No cleanup job, new service, paid backup feature, or public reset endpoint is introduced.

## Verification

The deployed Docker image started successfully with Java 21 and PostgreSQL 17.12. Flyway applied schema version 1 on the initial deployment. The reviewed deployment validated the existing schema without adding a migration. Initial browser acceptance covered member issue creation and comments, administrator assignment and resolution, activity history, and persistence after a page reload. The second review confirmed the same report, comment, assignment, and activity survived the deployment, and verified the corrected JSON request limit over public HTTPS. See the [verification record](VERIFICATION.md) for scope and limits.
