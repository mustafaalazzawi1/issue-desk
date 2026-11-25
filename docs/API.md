# API reference

All routes use the same origin. Protected routes require a session cookie. Every mutation, including login and logout, requires the CSRF header returned by `GET /api/session`. Fetch a new session response after signing in because authentication rotates the session and CSRF token. State-changing requests without a valid token return 403.

| Method | Route                       | Purpose                                                     |
| ------ | --------------------------- | ----------------------------------------------------------- |
| GET    | `/api/session`              | CSRF header/token, current public identity, demo flag       |
| POST   | `/api/login`                | URL-encoded `username` and `password`                       |
| POST   | `/api/logout`               | Invalidate the session; returns 204                         |
| GET    | `/api/health`               | Database readiness; returns `{"status":"UP"}`               |
| GET    | `/api/summary`              | Counts scoped to the user's visible issues                  |
| GET    | `/api/issues`               | Paginated visible issues; `q`, `status`, `priority`, `page` |
| POST   | `/api/issues`               | Create an issue owned by the authenticated user             |
| GET    | `/api/issues/{id}`          | Issue, comments, and activity                               |
| PATCH  | `/api/issues/{id}`          | Administrator triage with version precondition              |
| POST   | `/api/issues/{id}/comments` | Comment on a visible issue                                  |
| GET    | `/api/assignees`            | Administrator-only assignment options                       |

Create body:

```json
{
  "title": "Test application cannot export",
  "description": "Fictional steps to reproduce this demonstration issue.",
  "category": "APPLICATION",
  "priority": "MEDIUM"
}
```

Update body:

```json
{
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assigneeId": null,
  "version": 0
}
```

Comment body:

```json
{ "body": "Investigating with fictional sample data." }
```

Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`. Priority: `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Category: `ACCESS`, `NETWORK`, `APPLICATION`, `DEVICE`.

Page size is 20. Search is limited to 120 characters. Title: 3–120 characters; description: 10–5,000; comment: nonblank and at most 2,000. JSON requests are limited to 16 KiB, including chunked bodies and structured media types such as `application/problem+json` and `application/vnd.issue+json`. Oversized requests return HTTP 413 before JSON parsing. The shared demonstration is limited to 500 issues and 100 comments per issue. In demo mode, separate fixed one-minute request budgets apply to the whole app instance: 240 API requests, including at most 12 login attempts and 30 other mutations. Static files, `/api/health`, and `/api/logout` are exempt. A rate-limit response has `code: "demo_rate_limit"`, `Retry-After` (seconds), and `Cache-Control: no-store`. Wait before explicitly retrying; do not automatically replay a write. Shared limits reset on application restart and are not distributed across replicas.

Errors: 400 invalid input, 401 unauthenticated, 403 forbidden action or invalid CSRF, 404 missing or invisible issue, 409 stale edit, 413 oversized request, 429 demo capacity or request budget reached. Responses include `message` and optionally field errors. No password hashes are returned.
