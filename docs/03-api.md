# 03 · API reference

## SOAP

- Endpoint: `http://localhost:8080/ws`
- WSDL: `http://localhost:8080/ws/inventory.wsdl`
- Namespace: `https://stockbridge.example/inventory/v1` (an identifier, not an external service)
- SOAP 1.1, HTTP POST, `Content-Type: text/xml; charset=utf-8`.
- Dispatch uses the root payload name. A SOAPAction header is not required by this implementation.

| Operation | Request content | Response |
|---|---|---|
| listProducts | Empty request | Zero or more products |
| getProduct | sku | Product |
| createProduct | product: sku, name, category, price, quantity, reorderLevel | Product |
| restockProduct | sku, quantity | Updated product |
| placeOrder | customer, one or more lines: sku, quantity | Confirmed order |
| getOrder | id | Order and line items |
| cancelOrder | id | Cancelled order |

Element order must follow the XSD. For example `sku` precedes `quantity` in an order line. Java classes are generated in `target/generated-sources/jaxb` during `generate-sources`; do not manually copy classes from an unrelated WSDL.

Try a read-only request from the dashboard's SOAP explorer, or from the project root:

```sh
curl -sS http://localhost:8080/ws \
  -H 'Content-Type: text/xml; charset=utf-8' \
  --data-binary @examples/soap/list-products.xml
```

Import the WSDL URL into SoapUI to explore the service. The application must be running. `place-order.xml` changes inventory; repeating it creates a new order each time. The get/cancel order examples contain `REPLACE_WITH_ORDER_ID`, which must be replaced with an ID returned by placing an order.

### Business codes

| Code | Meaning | REST status |
|---|---|---|
| INVALID_INPUT | Invalid field or limit exceeded | 400 |
| NOT_FOUND | Unknown product or order | 404 |
| DUPLICATE_SKU | Product SKU already exists | 409 |
| INSUFFICIENT_STOCK | One or more order lines cannot be fulfilled | 409 |
| BUSY | REST database concurrency failure; refresh before retrying | 409 |

SOAP business faults use HTTP 500 as required by the SOAP 1.1 fault convention. Business codes appear in fault detail. Unexpected SOAP failures use `SERVER_ERROR`; schema validation faults use Spring-WS validation detail. A client must inspect the SOAP envelope, not assume HTTP 500 always means a server bug.

## REST for the dashboard

| Method | Path | Body / result |
|---|---|---|
| GET | /api/products | All products |
| GET | /api/products/{sku} | One product |
| POST | /api/products | `{ "sku":"NEW-1", "name":"New item", "category":"Accessories", "price":19.50, "quantity":10, "reorderLevel":3 }` |
| POST | /api/products/{sku}/restock | `{ "quantity":5 }` |
| GET | /api/orders | All orders, newest first |
| GET | /api/orders/{id} | One order |
| POST | /api/orders | `{ "customer":"Demo buyer", "lines":[{"sku":"KB-101","quantity":2}] }` |
| POST | /api/orders/{id}/cancel | Empty JSON object `{}` |
| GET | /api/movements | Latest 100 stock changes |
| GET | /api/summary | Catalogue, stock and confirmed order totals |

All prices are CAD, tax excluded. Successful creation returns HTTP 201 through REST and HTTP 200 through SOAP. There is no authentication in this local version.
