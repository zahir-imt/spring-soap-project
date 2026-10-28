# SlotRelay

Find your next opening. A separate appointment marketplace inside the StockBridge repository.

## What works
- Customer signup, sign-in and sign-out with Django password validation, hashed passwords, CSRF protection and login throttling.
- Business profiles, staff scheduling with overlap validation, CAD pricing and Toronto timezone.
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
This is an MVP for private preview, not a public production launch. No email/SMS delivery, payment processing, refunds, external calendar sync, email verification, self-service password recovery. In-app offers require customers to check their dashboard. Prices are CAD and all displayed appointments use America/Toronto. Explore results are paginated in groups of 24; the appointment dashboard shows the latest 50 bookings. SQLite serializes writes using immediate transactions and a unique active-booking constraint. Before scaling, migrate to PostgreSQL, add pagination, monitoring, stronger abuse controls, account recovery, business verification, persistent hosting/backups and notification delivery. Account-level throttling can temporarily block a username after repeated failed attempts.

The public repository contains source only. Private Codespaces forwarding protects the preview; repository visibility is separate from application access. Do not expose this preview publicly as a production service.

## Calendar, analytics and rescheduling

- Customers: open **My appointments → Reschedule appointment** on a confirmed future booking. Choose another available time for the same business, service and duration. The new time and listed CAD price are shown before confirmation. If it becomes unavailable, the original booking remains confirmed. Successful moves release the old time to its FIFO waitlist and retain a Rescheduled history record.
- Businesses: open **For businesses → Calendar & analytics**. Select a date and day/week view, or use previous/next/today navigation. All boundaries and times use America/Toronto. The calendar includes available, booked, cancelled and waitlist-reserved appointments; customer names are private to the business owner.
- Analytics use the appointment dates in the selected period: confirmed bookings/value, cancelled booking records, reschedules from the period and historical accepted waitlist offers. Reschedules are excluded from cancellations. Accepted offers remain counted if later cancelled. Listed value is not payment revenue; past bookings do not prove attendance.
- Startup applies migration 0002 to add reschedule history. Back up the SQLite database before upgrading an existing workspace. No payment or email service is introduced.


## Five workflow upgrades — October 2026

1. **Staff scheduling:** For businesses → Manage staff. Add or deactivate staff and select a staff member when publishing. Different staff may work simultaneously. Unassigned appointments, including existing appointments, reserve the whole business. Deactivation prevents new assignments and preserves existing appointments; staff records are not login accounts.
2. **Batch publishing:** For businesses → Publish in batches. Create 2–50 consecutive appointments with optional 0–120 minute breaks. Conflicts roll back the entire batch. Times are entered in Toronto time; intervals use elapsed minutes across daylight saving changes.
3. **Flexible waitlists:** My appointments → Join a flexible waitlist. Choose a business, exact service name, future start-time window (up to 31 days), and maximum CAD price. Join matching currently listed busy appointments across staff; available openings and existing entries are skipped. Up to 100 candidate appointments and 10 active groups. New listings are not watched. Existing queue priority is preserved; entries whose group already holds an offer are temporarily skipped. Only one offer per group is held at a time. Accepting a matched appointment closes the group's other entries. Close a group from the dashboard. No automatic bookings.
4. **Calendar export:** My appointments → Export to calendar. Private, owner-only ICS files include UTC start/end, location, staff and cancellation status. Import into a compatible calendar; exports are snapshots, not subscriptions, and must be re-exported after changes.
5. **Notification inbox:** Updates shows the unread count. View all or unread notifications, paginate, and mark one or all as read. Delivery remains in-app only.

Migration 0003 preserves accounts, bookings, waitlists and notices. Back up the database before upgrading. Existing notices initially appear unread. Run migrations, then restart both the web server and offer worker. Production use on PostgreSQL would require revisiting cross-slot flexible-offer locking; current concurrency guarantees are tested on SQLite IMMEDIATE transactions.


## Discovery improvements — 2026-10-28
Explore now searches service and business names and supports category, city, inclusive Toronto date range, maximum CAD price, availability and price/time sorting. Results are paginated 24 at a time with stable ordering and filter-preserving links. Invalid filters show validation messages instead of silently returning broad results. Reset filters restores the full list of upcoming appointments. Booked appointments and active waitlist holds are excluded from Available to book. No bookings or prices are changed by searching.

Validation: 74 Django tests pass, including eight discovery tests, plus four SQLite concurrency checks. No schema changes. Existing fictional demo appointments may have passed; an empty upcoming list is not a server error.
