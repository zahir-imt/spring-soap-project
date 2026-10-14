# SlotRelay MVP validation

Validated in GitHub Codespaces on 2026-10-13. No development runtime was installed on the user's Mac.

- Django test suite: 25 tests passed. Includes an HTTP-level business publication, customer booking, second-customer waitlist, cancellation, offer acceptance and business dashboard journey.
- Independent file-backed SQLite concurrency check: two simultaneous booking attempts produced one confirmation and one rejection.
- Django system check: no issues. Migration drift check: no changes.
- Browser: private port 8000 loads the marketplace; category/city filtering returned the expected fictional guitar lesson; sign-in page loads. Phone-width checks covered homepage and sign-in.
- Authenticated workflows tested through Django clients, not a manually entered user's browser account.
- Demo seed contains six clearly labelled fictional businesses; no usable demo-owner passwords.
- StockBridge working copy retains its prior scripts/run-online.sh modification and cloud-finalize.py. Only SlotRelay and its own CI workflow are committed here.

This is a private Codespaces preview, not permanent public hosting. Payments, email/SMS, account recovery, external calendars and business verification are not implemented.

## Signup CSRF regression (2026-10-13)

The live Codespaces proxy sent `Origin: http://localhost:8000`, which was rejected by Django. The exact loopback origin is now trusted only when `CODESPACE_NAME` is set. CSRF token and cookie checks remain enabled. Added tests for real token-based signup, public HTTPS origin, hostile origin, missing cookie recovery, and mismatched token rejection. Rejections show a safe recovery page and log the reason without form credentials.

## Calendar and rescheduling update (2026-10-14)

- 44 Django tests passed, including 14 new workflow tests for reschedule ownership, CSRF, rollback, unavailable targets, same-service restrictions, FIFO offers, history, calendar owner isolation, period analytics, invalid dates and Toronto midnight boundaries.
- Disposable file-backed SQLite concurrency checks passed for competing bookings and rescheduling versus booking the same target; exactly one target booking survives, and a failed reschedule keeps the original.
- Django system checks and migration-drift checks passed. Existing cloud database backed up before applying migration 0002.
- Screen responses are covered by Django HTTP tests; live browser review is pending preview reconnection.
