# Build StockBridge inventory and order management with SOAP and a dashboard

The starter application had no implemented SOAP operations or business workflow. This change adds a local inventory application with a product catalogue, restocking, multi-line orders, cancellation, stock history and a browser dashboard.

REST and SOAP adapters share a transactional service. Product row locks prevent overselling; failed multi-line orders roll back; repeated cancellation restores stock once. An XSD generates the Java message classes and publishes seven operations through WSDL.

Validation includes integration tests for rollback, concurrent ordering/cancellation, input errors and real SOAP/REST requests, plus five Cucumber scenarios. See `docs/06-results.md` for the executed local results and the Actions check for remote results once available.

The implementation retains the starter's Spring Boot 3.x and Cucumber direction but introduces a local inventory contract instead of depending on the README's public sample service. Documentation includes requirements, architecture diagrams, interface examples, demonstration steps and beginner setup instructions.

Scope: a local demonstration using H2 file storage, without authentication, payment processing or production deployment. Order creation does not yet deduplicate repeated requests.
