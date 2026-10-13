# SlotRelay

Find your next opening. A separate appointment marketplace inside the StockBridge repository.

## What works
- Customer signup, sign-in and sign-out with Django password validation, hashed passwords, CSRF protection and login throttling.
- Business profiles, one appointment at a time per business, overlap validation, CAD pricing and Toronto timezone.
- Search by service, category and city; book a slot or join its first-in-first-out waitlist.
- Customer cancellation offers the slot to the next waiter for 15 minutes (or until the appointment begins). Decline or expiry advances the queue. Offers need explicit acceptance; no automatic purchase.
- Business cancellation closes bookings and waiting entries, with private in-app updates.
- Separate customer/business dashboards. Business value means listed booking value, not collected revenue.
- Persistent SQLite database, migrations, background offer worker, responsive accessible HTML.

## Start entirely in GitHub Codespaces
Open a Codespace on branch codex/slotrelay. In its cloud terminal, from the repository root:

    cd slotrelay
    bash run-cloud.sh

Open port 8000 from the Codespaces Ports panel and keep it Private. The application starts at /. Create a SlotRelay account with /signup/; there is no default password and StockBridge credentials are separate. To publish, choose For businesses and create your business profile.

The Codespace needs Python 3.11+ and python3-venv. If absent in the cloud container, install python3-venv using the container package manager. No Mac installation is needed. StockBridge remains on port 8080 with its separate database and runtime.

## Optional fictional listings

    .venv/bin/python manage.py seed_demo

Every sample business is labelled Demo, has a fictional address and has no usable owner login. Never attend or pay for demo listings. This command is opt-in and idempotent for future demo slots.

## Tests

    .venv/bin/python manage.py test booking
    .venv/bin/python scripts/check_concurrency.py
    .venv/bin/python manage.py check
    .venv/bin/python manage.py makemigrations --check --dry-run

Tests use an isolated test database, not app data. They cover booking exclusivity, FIFO offers, expiry, cancellation, authorization, CSRF, password validation, throttling, overlapping slots, search, templates and private notifications.

## Data & operations
Runtime data and a generated secret key stay under slotrelay/data/ and are ignored by Git. Back up that directory while stopped. Restarting the same Codespace preserves data; deleting the Codespace does not. Keep independent backups before deletion. The offer worker runs every 30 seconds; reading the marketplace/dashboard also refreshes offers.

## Scope and next steps
This is an MVP for private preview, not a public production launch. No email/SMS delivery, payment processing, refunds, external calendar sync, email verification, self-service password recovery or multi-staff calendars. In-app offers require customers to check their dashboard. Prices are CAD and all displayed appointments use America/Toronto. Browse and dashboard lists are capped at 100 and 50 items respectively. SQLite serializes writes using immediate transactions and a unique active-booking constraint. Before scaling, migrate to PostgreSQL, add pagination, monitoring, stronger abuse controls, account recovery, business verification, persistent hosting/backups and notification delivery. Account-level throttling can temporarily block a username after repeated failed attempts.

The public repository contains source only. Private Codespaces forwarding protects the preview; repository visibility is separate from application access. Do not expose this preview publicly as a production service.
