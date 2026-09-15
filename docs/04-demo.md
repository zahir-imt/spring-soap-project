# 04 · Demonstration script

Allow about 8–10 minutes. The sequence works with existing data; use the observed starting stock rather than assuming a fresh database.

## 1. Explain the problem (30 seconds)

“StockBridge lets a warehouse manage products and orders through a browser and a SOAP service. Both interfaces use the same stock rules. I will show a successful order, a rejected order and safe cancellation.”

## 2. Show the dashboard (1 minute)

Open `http://localhost:8080`. Explain the four cards: number of products, available units, inventory value at selling prices, and confirmed orders. Point out low-stock products. These are real database values.

## 3. Place an order (2 minutes)

1. Go to Products. Note the current stock of Mechanical keyboard (`KB-101`). If it has fewer than two units, use Restock to add five.
2. Click Create order.
3. Enter `Demo customer` as the customer.
4. Select Mechanical keyboard and quantity `2`.
5. Click Create order.
6. Confirm the order total is CAD 178.00 and stock fell by two. Show the new movement in Stock activity.

## 4. Demonstrate a rejected order (1 minute)

1. Create another order for `KB-101` with quantity one greater than its displayed stock (within the allowed maximum).
2. Show the insufficient-stock message.
3. Close the form. Show that stock and the order count did not change.

## 5. Cancel the order (1 minute)

1. Open Orders and Details for the successful order.
2. Click Cancel this order.
3. Show the Cancelled status, restored stock and cancellation movement.

## 6. Show SOAP (1–2 minutes)

1. Open SOAP explorer and send List products.
2. Explain the XML request and real response.
3. Choose Product not found. Send it and show the SOAP fault.
4. Open the WSDL link. Explain that the contract describes the available operations and data shapes.

## 7. Show evidence (1 minute)

Open `docs/evidence/cucumber-report.html` for the saved tested run, or double-click `RUN-TESTS.command` to generate a fresh report. Show readable scenarios and passing results. The saved evidence is a snapshot, not a live test status badge.

## 8. Explain one design decision

“Orders update stock inside a database transaction. If one line fails, the whole order rolls back. Row locks prevent two orders from reserving the same units. Cancellation checks the locked order status, so repeating it does not restore stock twice.”

Finish by showing the limitations and roadmap. Do not call the project production-ready or claim payment, shipping or authentication features.
