# QuoteCraft validation

Validated entirely in GitHub Codespaces.

- Maven verify: **24 tests, zero failures, zero errors**.
- Packaged application HTTP smoke test: registration, login, creation of a two-line quote, discount-before-tax calculation (CAD 271.20), review-link creation, PDF response, customer approval, scheduling, starting, completing, job board, and logout all passed.
- Tests use fictional accounts and a separate in-memory database; preview accounts are not modified.
- Security cases: business isolation, foreign PDF rejection, CSRF enforcement, escaped HTML, token validation, expired and withdrawn links, and one winning response under concurrent requests.
- Browser: homepage and account-creation page load through the private Codespaces HTTPS forwarded port on 8081; homepage layout inspected.
- Spring Boot jar builds and starts successfully with a persistent database separate from StockBridge and SlotRelay.

Automated tests do not establish production readiness or verify PostgreSQL deployment. Public hosting, payments and email delivery are outside this release.
