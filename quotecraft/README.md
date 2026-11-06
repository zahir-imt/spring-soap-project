# QuoteCraft

A Spring Boot workspace for small cleaning businesses: prepare an itemised quote, share a customer review link, and track approved work through completion.

## Included

- Business registration and sign-in with BCrypt passwords; each business sees only its own quotes.
- Customer names, emails, service addresses, scope and terms; searchable customer quote history.
- Up to 20 service lines, quantities, unit prices, fixed CAD discounts, and configurable percentage tax.
- Server-calculated decimal totals: line amounts round half-up to cents; discount comes before tax.
- Drafts, shared quotes, approvals, declines, requests for changes, and withdrawal.
- Revised copies preserve previously shared documents. Withdraw the original when it should no longer be accepted.
- Random 256-bit review links, expiry dates in Toronto time, CSRF protection, atomic single-response processing.
- PDF quotes with automatic pagination.
- Job board: unscheduled, scheduled, in progress, completed.
- Responsive dashboard, quote editor, and customer review pages.

## Run entirely in GitHub Codespaces

Use the Codespaces terminal, not your Mac terminal:

    cd /workspaces/quotecraft-worktree/quotecraft
    bash scripts/start-online.sh

Open port **8081** from the Ports panel. StockBridge uses 8080 and SlotRelay uses 8000. Register your own business account using **Create workspace**. Passwords must have at least 12 characters and no more than 72 UTF-8 bytes. There is no default account or password.

The helper runs in the foreground. Stopping the Codespace stops the application. Code pushed to GitHub remains saved, but a Codespaces preview is not permanent hosting.

## Workflow

1. Create your business account and sign in.
2. Select **New quote**, enter the customer, service address, line items, discount, tax and expiry.
3. Save the draft, check final totals, and optionally download the PDF.
4. Select **Create review link** and copy it to your customer. The app does not send email or SMS.
5. The customer approves, declines, or requests changes. Whoever holds the link can respond; this is not verified electronic identity.
6. Schedule an approved job and advance it to in progress and completed.

Private Codespaces ports require GitHub access in addition to the review link. Production hosting and access settings should be configured separately.

## Data and configuration

Java 17, Spring Boot 3.5.16, Spring MVC, Security, JDBC, Thymeleaf and PDFBox. The preview uses a persistent H2 database at data/quotecraft; data and credentials are excluded from Git. Back up that directory while the app is stopped. Deleting a Codespace can remove its database.

PORT defaults to 8081. DATABASE_URL, DATABASE_USER, and DATABASE_PASSWORD can point to PostgreSQL (JDBC URL). PostgreSQL driver/schema are supplied; automated verification currently uses H2. No production PostgreSQL deployment has been validated.

## Tests

    bash mvnw --batch-mode verify

Tests cover monetary rounding, validation, hashed passwords, account isolation, unpredictable tokens, expiry, revoked links, simultaneous responses, job transitions, CSRF, HTML escaping, template rendering and PDF contents. GitHub Actions runs the same command.

## First-version boundaries

This is a working portfolio/pilot release, not a payment processor or accounting system. Approved value is quoted value, not revenue received. Payments, email delivery, email verification, password recovery, team membership, logo uploads, subscriptions and automatic reminders are not included. PDF export uses a standard Latin font; unsupported characters are replaced with question marks while browser pages preserve Unicode. Before public commercial deployment, add managed database backups, abuse/rate limiting, account recovery, observability and production hosting configuration.

StockBridge and SlotRelay are separate projects and are not modified by this module.
