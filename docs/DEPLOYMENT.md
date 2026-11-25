# Live deployment

[Open Issue Desk](https://issue-desk-production.up.railway.app/)

Verified November 25, 2026. The public application runs the repository Dockerfile on Railway Free and uses a dedicated Neon Free PostgreSQL 17 database. The Railway deployment is pinned to source commit `5499e4e02c4b35f1de9bc7ef2af3b60dc7e4c2bc`; subsequent documentation edits do not rebuild the application automatically.

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

The seeded accounts are intentionally public. Visitors share data; use fictional information only. The dataset is not automatically reset. The application limits total issues and comments per issue, but this is not a substitute for a provider abuse-protection or rate-limiting service.

## Verification

The deployed Docker image started successfully with Java 21 and PostgreSQL 17.12. Flyway applied schema version 1. Browser acceptance covered member issue creation and comments, administrator assignment and resolution, activity history, and persistence after a page reload. See the [verification record](VERIFICATION.md) for scope and limits.
