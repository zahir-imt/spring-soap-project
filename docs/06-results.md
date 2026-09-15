# 06 · Verified outcomes

## Executed local results — 15 September 2026

**BUILD SUCCESS: 19 tests, 0 failures, 0 errors, 0 skipped.**

- 14 integration tests: transactional rollback, exact totals, duplicate/invalid inputs, low-stock reporting, concurrent ordering and cancellation, WSDL, schema validation, SOAP faults, all mutation operations, and REST/browser page delivery.
- 5 Cucumber scenarios using actual HTTP SOAP requests: successful ordering, insufficient stock, invalid quantity, repeated cancellation and restocking.
- Platform: macOS arm64, Temurin Java 17.0.20.1, Maven 3.9.11, Spring Boot 3.5.16.
- Command: `mvn clean verify` using the project-local Java/Maven tools. Source was formatted before the final clean run.

## Browser checks performed

| Action | Observed result |
|---|---|
| Create Desktop speaker, DS-707 | Product appears with 12 units and CAD 45.00 price |
| Place two-keyboard order | Stock falls from 42 to 40; saved total is CAD 178.00 |
| Cancel that order | Stock returns to 42; order remains visible as Cancelled |
| SOAP explorer: list products | HTTP 200 and actual product XML |
| SOAP explorer: missing product | HTTP 500 SOAP Client fault with `NOT_FOUND` detail |
| Restart the application process | Created product and cancelled order remain; DS-707 still has 12 units |

The delivered local database contains the six initial seed products, the extra demonstration speaker created during browser verification, and the cancelled demonstration order. A fresh source checkout starts with six seed products and no orders.

## Evidence

- [Saved Cucumber HTML report](evidence/cucumber-report.html) — open the downloaded/local file in a browser; GitHub's normal file view does not execute HTML.
- [Machine-readable verification summary](evidence/verification.json).
- [Build result summary](evidence/build-summary.txt).
- [Source SHA-256 hashes](evidence/source-sha256.txt) identify the tested Java/configuration/frontend/test files.
- [Dashboard screenshot](images/dashboard.png).
- [SOAP explorer screenshot](images/soap-explorer.png).

These are snapshots of the executed local run. For fresh results, run `RUN-TESTS.command` or `./mvnw clean verify`. The new report appears in `target/cucumber-report.html`; the saved docs evidence is not overwritten automatically.

## GitHub status

The original repository history is preserved on branch `codex/stockbridge-inventory`. Use the repository branch and Pull requests pages for current publication status. The Actions workflow runs when the branch is pushed. Follow `docs/08-online-only.md` for the browser-only workflow.

## Practical limitations

This is a working local portfolio version, not a production deployment. It has no login/roles, no payment or shipping integration, no public hosting and no order-placement request deduplication. It is tested on H2, not PostgreSQL. Lists are not paginated. Desktop browser interactions were checked; mobile CSS is present but mobile interaction testing has not been performed. Mac Intel and Windows instructions have not been executed here. The original public sample SOAP service is not called by this implementation.

The Mac launcher was tested. In this automation environment opening the default external browser failed, so the launcher now continues running and prints the URL as a fallback; the in-app browser successfully reached the application.

## Cloud verification — 15 September 2026

Rebuilt in GitHub Codespaces on Linux with Microsoft OpenJDK 17.0.20.1. All 19 tests passed with zero failures, errors or skips. The uploaded source hashes matched the locally tested source. [Cloud build summary](evidence/cloud-build-summary.txt) and [cloud Cucumber report](evidence/cloud-cucumber-report.html) record this separate run. The cloud database starts with the six seed products; local demo data was not uploaded.
