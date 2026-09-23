# StockBridge 1.1 upgrade guide

## Start and sign in

Requires Java 17+. Run `bash mvnw --batch-mode clean verify` to build and test.
Before the first application startup, set `STOCKBRIDGE_ADMIN_PASSWORD` to a strong password of at least 12 characters and at most 72 UTF-8 bytes. Start with `bash scripts/run-online.sh`, or `java -jar target/stockbridge-1.0.0.jar`. The artifact filename is retained for existing helper scripts. Sign in as `admin` with the password you supplied. Bootstrap stores a BCrypt hash, not plaintext. Subsequent starts reuse the database account; changing the environment variable does not reset it.

Use Team access to create accounts with individual passwords. This release creates accounts but does not yet provide self-service password recovery, editing roles, or disabling users. Keep deployment private; production use still needs HTTPS, a recovery process, login throttling, backups and operational review.

## Permissions

| Operation | Admin | Warehouse | Sales |
| --- | --- | --- | --- |
| Read catalogue, orders, movements, summary and alerts | Yes | Yes | Yes |
| Create products and restock | Yes | Yes | No |
| View/manage suppliers and purchases; receive deliveries | Yes | Yes | No |
| Place/cancel customer orders | Yes | No | Yes |
| List/create user accounts | Yes | No | No |

Permissions are enforced on the server for REST and the corresponding SOAP operations. Hiding controls is only a convenience. SOAP requests require authentication and CSRF too.

## Purchase workflow

1. Add a supplier under Purchasing.
2. Create a purchase order with a supplier, unique product lines, positive quantities and nonnegative unit costs with at most two decimal places. Cost is separate from catalogue selling price.
3. The order starts as ORDERED. Available stock does not change. No email, payment or external supplier submission occurs.
4. When all items arrive, open Details and Confirm full delivery. Every line updates stock and records its purchase ID in movement history in one transaction.
5. Repeating receipt, even concurrently, returns RECEIVED without adding stock again. A failed line rolls back the whole receipt. Pending orders can be cancelled; received orders cannot be cancelled.

This version supports full delivery only, not partial receipts, supplier returns, promised delivery dates or editing issued purchases.

## Low-stock alerts

Products at or below their reorder level appear on Low-stock alerts. For each product:

`suggested quantity = max(0, reorder level + 1 - available quantity - pending purchase quantity)`

Incoming units are shown separately from available units. Cancelled and received purchase orders do not count as pending. An alert remains until actual available stock rises above the threshold; 'Covered by incoming stock' explains why no additional purchase is suggested. Alerts update when dashboard data refreshes after an action or a page reload. This release has in-app alerts, not email/SMS notifications or background polling.

## Duplicate customer orders

REST `POST /api/orders` now requires `requestId` alongside `customer` and `lines`. SOAP `placeOrderRequest` requires `requestId` before `customer`; regenerate clients from the WSDL. Example:

```json
{"requestId":"a-client-generated-uuid","customer":"Example customer","lines":[{"sku":"KB-101","quantity":2}]}
```

Use one stable ID for all retries of a single order; use a fresh ID for a new order. The dashboard creates the ID when the order form opens and retains it for retries within that form. Closing or reloading the form begins a new request. If a response is lost and you leave the form, check Orders before starting again.

Same ID + same normalized customer/lines returns the existing order. Same ID + different details produces a conflict. A failed transaction does not consume the ID. Replaying a cancelled order returns its cancelled state and does not create another order. Request IDs are workspace-wide, not per user, and must be unique. A database guard row serializes order placements for straightforward correctness; a higher-volume deployment should use more granular coordination.

## HTTP authentication and CSRF

The browser uses a session created by Spring Security form login. All writes, including SOAP and logout, require a CSRF token. `GET /api/csrf` returns `token`, `headerName`, and `parameterName`; retain its session cookie and send the token under the returned header on writes. Obtain a fresh token after login. Integrations can use HTTP Basic authentication plus that same cookie/token exchange. Use TLS outside localhost.

Additional REST routes:

- `GET /api/me`, `GET /api/csrf`
- `GET/POST /api/users` — account creation: username, password, role
- `GET/POST /api/suppliers` — supplier creation: name, email
- `GET/POST /api/purchases` — creation: supplierId, lines of sku/quantity/unitCost
- `POST /api/purchases/{id}/receive`, `POST /api/purchases/{id}/cancel`
- `GET /api/alerts`

Existing routes remain, subject to authentication, permissions and the request-ID change. All authenticated roles can view customer order data; this is a single-workspace application, not a multi-tenant service.

## Upgrade existing data

Stop the app and back up its `data/` directory. Start the new build against the existing database with the initial admin password supplied. Initialization creates new tables using `IF NOT EXISTS`; it does not replace existing product, order, or movement tables. Existing orders have no request IDs and cannot be retroactively deduplicated. Do not run old and new application versions concurrently against the same file database.

## Validation

See [upgrade validation](upgrade-validation.md). The automated suite covers original SOAP/REST behaviour, roles and login, CSRF rejection, purchase transitions, low-stock calculations, receipt rollback, concurrent receipts, and duplicate order retries. Browser checks use a separate disposable in-memory database.
