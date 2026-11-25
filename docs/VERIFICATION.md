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
| GitHub Actions                         | [Passed on GitHub Actions](https://github.com/mustafaalazzawi1/issue-desk/actions/runs/45583785880), source commit `6770702`                                                                                               |
| macOS, Docker, public HTTPS hosting    | Not executed in this environment                                                                                                             |

The integration suite includes simultaneous competing updates, not just sequential stale requests: exactly one save succeeds and one returns 409. It also tests unauthorized reads/comments, admin-only mutations, literal wildcard/SQL-like search, malformed input, chunked request-size limits, CSRF enforcement, session rotation, cookie attributes, and audit consistency.

The narrow-width checks used desktop Chromium zoom. They are not a claim of testing physical mobile devices or mobile Safari. A complete accessibility audit, load test, and independent security audit have not been performed.

Source commit `6770702` passed hosted GitHub Actions on November 25, 2026. A dedicated Neon PostgreSQL 17 project has been provisioned. The web service has not been deployed; hosting selection and public HTTPS verification remain pending. No live-demo URL is claimed yet. The existing portfolio has not been changed during this publication pass.
