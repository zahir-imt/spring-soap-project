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
