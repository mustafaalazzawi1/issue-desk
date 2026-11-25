# Verification record

## v0.1.1: demo request budgets

Local verification on November 25, 2026 passed **28 tests**: 19 existing real HTTP/PostgreSQL workflow tests, one additional real HTTP test with rate limits enabled, and 8 request-budget tests. No failures, errors, or skips. The production JAR smoke test and JavaScript/shell syntax checks also passed.

New coverage includes real login/API throttling through Spring Security, window reset and `Retry-After`, simultaneous requests, separate login/write budgets, changed sessions and forwarded addresses, demo-only activation, and health/static/logout exemptions. The original workflow suite disables the aggregate budget so that unrelated test traffic cannot interfere with its assertions; the dedicated HTTP test explicitly enables it.

The owner maintenance script is read-only and the backup-first runbook is a plan. No live records have been deleted and a backup restore drill has not yet been executed. [Latest CI results](https://github.com/mustafaalazzawi1/issue-desk/actions/workflows/ci.yml) identify the tested source. Hosted deployment acceptance for this revision is recorded below once complete.

## Earlier deployed baseline: v0.1.0

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
| GitHub Actions                         | [Passed for the deployed source](https://github.com/mustafaalazzawi1/issue-desk/actions/runs/45592145187), source commit `fdf6ab4`; all 19 integration tests, packaged-JAR smoke test, and browser-script syntax |
| Railway Docker deployment              | Built and started successfully from the repository Dockerfile; Java 21, PostgreSQL 17.12, Flyway schema version 1 |
| Public HTTPS browser acceptance        | Passed: member creation/comment, administrator assignment/resolution, activity history, and reload persistence |
| Initial hosted HTTP checks            | Eight groups passed on `5499e4e`: database health, anonymous rejection, cookie/security headers, member ownership, role/CSRF denial, cross-member isolation, administrator access, logout invalidation |
| Second hosted review                  | Passed on `fdf6ab4`: database health, anonymous rejection, six oversized-JSON requests, administrator sign-in, persisted report/comment/activity, search empty state and clear-filters recovery |
| macOS and local Docker Compose         | Not executed in this environment |

The integration suite includes simultaneous competing updates, not just sequential stale requests: exactly one save succeeds and one returns 409. It also tests unauthorized reads/comments, admin-only mutations, literal wildcard/SQL-like search, malformed input, chunked request-size limits, CSRF enforcement, session rotation, cookie attributes, and audit consistency.

The narrow-width checks used desktop Chromium zoom. They are not a claim of testing physical mobile devices or mobile Safari. A complete accessibility audit, load test, and independent security audit have not been performed.

The [public demo](https://issue-desk-production.up.railway.app/) was rechecked on November 25, 2026, running source commit `fdf6ab4663229e88604c63626247e5e7c401c86b`. The browser acceptance record, ISS-009, contains only fictional test data and remains resolved. Its comment, administrator assignment, and activity history survived the deployment. A screenshot from the running application and the live-demo link are included in the repository and portfolio. The GitHub profile links to the app and includes it among the pinned projects.

## Second review: structured JSON request limits

The review found that the 16 KiB request filter covered `application/json` but missed structured JSON media types accepted by Spring. A new regression case reproduced an oversized `application/problem+json` request returning 400 instead of the intended early 413. The filter now handles both `application/json` and `application/*+json`, including media-type parameters.

The existing body-size test now checks three media types with both known-length and chunked bodies. After the fix, all 19 integration tests and the packaged-JAR smoke test passed locally and in GitHub Actions. Against the deployed application, all six oversized requests returned 413 with the expected size-limit message; database health returned 200 and anonymous issue access returned 401. These checks did not create new reports or comments. The review was a focused functional and code review, not an independent security audit.

The first hosted startup failed because the PostgreSQL driver expected a local root certificate. The service configuration now uses the JRE trust store with `sslmode=verify-full`; the replacement deployment started successfully. See [deployment configuration and Free-plan limits](DEPLOYMENT.md). No TLS validation was disabled.
