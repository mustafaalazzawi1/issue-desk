# Verification record

Rebuilt and rechecked from a fresh source copy. Verified on Linux with Temurin 21.0.11+10, Spring Boot 3.5.16, and PostgreSQL 17.12.

| Check                                  | Result                                                                                                                                       |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Real HTTP/PostgreSQL integration suite | 19 tests passed; zero failures, errors, or skips                                                                                             |
| Production JAR smoke test              | Passed: static assets, database health, sign-in, member-scoped reports                                                                       |
| Browser workflow                       | Passed: member sign-in, creation, comment, activity, sign-out, administrator sign-in, assignment, resolution, reload persistence             |
| Search and empty state                 | Passed in the browser, with clear-filters recovery                                                                                           |
| Keyboard history tabs                  | Arrow-key navigation switched Discussion/Activity correctly                                                                                  |
| Compact layout                         | Inspected at 539 and 337 CSS pixels using browser zoom; no horizontal document overflow; new-issue dialog and form widths remained contained |
| JavaScript and shell syntax            | Checked locally                                                                                                                              |
| GitHub Actions                         | [Passed for the deployed source](https://github.com/mustafaalazzawi1/issue-desk/actions/runs/45586489178), source commit `5499e4e`; all 19 integration tests and packaged-JAR smoke test |
| Railway Docker deployment              | Built and started successfully from the repository Dockerfile; Java 21, PostgreSQL 17.12, Flyway schema version 1 |
| Public HTTPS browser acceptance        | Passed: member creation/comment, administrator assignment/resolution, activity history, and reload persistence |
| Hosted HTTP checks                    | Eight groups passed: database health, anonymous rejection, cookie/security headers, member ownership, role/CSRF denial, cross-member isolation, administrator access, logout invalidation |
| macOS and local Docker Compose         | Not executed in this environment |

The integration suite includes simultaneous competing updates, not just sequential stale requests: exactly one save succeeds and one returns 409. It also tests unauthorized reads/comments, admin-only mutations, literal wildcard/SQL-like search, malformed input, chunked request-size limits, CSRF enforcement, session rotation, cookie attributes, and audit consistency.

The narrow-width checks used desktop Chromium zoom. They are not a claim of testing physical mobile devices or mobile Safari. A complete accessibility audit, load test, and independent security audit have not been performed.

The [public demo](https://issue-desk-production.up.railway.app/) was verified on November 25, 2026, running source commit `5499e4e02c4b35f1de9bc7ef2af3b60dc7e4c2bc`. The browser acceptance record, ISS-009, contains only fictional test data and remains resolved. A screenshot from the running application and the live-demo link were added to the portfolio.

The first hosted startup failed because the PostgreSQL driver expected a local root certificate. The service configuration now uses the JRE trust store with `sslmode=verify-full`; the replacement deployment started successfully. See [deployment configuration and Free-plan limits](DEPLOYMENT.md). No TLS validation was disabled.
