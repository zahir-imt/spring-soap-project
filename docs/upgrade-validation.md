# StockBridge upgrade validation

The final local Java 17 Maven `clean verify` run passed **33 tests**, with zero failures, errors or skipped tests, and produced the runnable application.

Coverage includes the original REST/SOAP and Cucumber flows; login and CSRF enforcement; server-side role restrictions including SOAP; hashed staff-account creation; repeated and concurrent customer-order submissions; request-ID conflicts and retries after failure; purchase validation, cancellation, receipt and rollback; concurrent receipts; incoming-stock replenishment calculations; and preservation of existing products, orders and movements when the new schema is applied twice.

Browser checks used a disposable in-memory database: administrator login, supplier creation, purchase creation from a low-stock alert, incoming-stock display, full receipt, staff-account creation, logout and sales-role login. Receiving six laptop stands increased available units from 94 to 100 and removed that product from the low-stock list. Sales users could see orders but not purchasing, team administration or restock controls.

JavaScript syntax, launcher shell syntax and Git whitespace checks passed. The build reports an existing Spring SOAP configuration deprecation warning; it does not fail the build.

A separate GitHub Codespaces checkout also passed all 33 tests with zero failures, errors or skips before publication. Publication status is tracked by the repository pull request; no deployment was performed. See the upgrade guide for setup and current limitations, including full deliveries only, in-app alerts and the required request ID for SOAP/REST order creation.
