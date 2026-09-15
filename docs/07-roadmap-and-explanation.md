# 07 · Understand the project and grow it

## A short explanation you can use

StockBridge is a Java inventory and order application. A browser user or a SOAP integration can create products, add stock, place orders and cancel orders. Both routes go through the same service. That service validates inputs and changes the database inside transactions. The project includes generated SOAP message classes, readable Cucumber tests and a dashboard showing the actual saved data.

## Questions Mr Zahir might ask

**What is SOAP?** A standard way to exchange structured XML messages between applications.

**What is WSDL?** The service description: it lists operations, message shapes and the endpoint. Our WSDL is published at `/ws/inventory.wsdl`.

**What is XSD?** A definition of the XML data structure. We generate Java message classes from our XSD rather than typing them by hand.

**What does Spring Boot do?** It configures and runs the Java application, web server and framework components.

**What is a transaction?** A group of database changes that either all succeed or all roll back. An order should not reduce stock if another line in that same order fails.

**Why lock a product row?** Two customers may order the last units at the same time. A database lock makes one transaction wait so it checks the updated stock before proceeding.

**What is idempotent cancellation?** Sending the same cancellation again has the same final effect as sending it once. It does not add stock twice. This guarantee does not yet apply to order placement.

**Why use Cucumber?** It connects readable Given/When/Then scenarios to executable tests. Here the scenarios send real SOAP HTTP requests to the local application.

**Why are there REST endpoints as well as SOAP?** JSON REST calls are convenient for this browser UI. SOAP is the formal integration interface. Shared business logic keeps their behaviour aligned.

**Is the dashboard using mock data?** Seed products are demonstration data saved to a real local database. The cards, forms and tables read/write that database through the backend.

**Did you write everything alone?** Be accurate: the implementation was created with Codex assistance. Explain what you reviewed, ran and learned. The repository README records that assistance.

## Growth plan

Version 1 is complete when the documented local application, tests and demo work. A bigger system should grow in separately tested releases:

| Release | Addition | Completion evidence |
|---|---|---|
| 1.1 | Idempotency keys for order placement | Duplicate requests return one order; concurrent duplicate requests tested |
| 1.2 | Product editing and archival, pagination | Historical orders keep original prices; large catalogue browsing tested |
| 2.0 | Users and operator/admin roles | Unauthorised operations fail; login/logout and permissions tested |
| 2.1 | PostgreSQL and schema migrations | Fresh install, migration and concurrent transaction tests pass on PostgreSQL |
| 3.0 | Multiple warehouses and transfer workflow | Transfers cannot lose/create units; per-warehouse stock reconciles |
| 3.1 | Supplier purchase orders and receiving | Partial receiving updates correct quantities with audit records |
| 4.0 | Hosted environment, backups and monitoring | Restore exercise, uptime monitoring and deployment rollback demonstrated |

No deadlines are assumed. Finish and understand one release before claiming the next.
