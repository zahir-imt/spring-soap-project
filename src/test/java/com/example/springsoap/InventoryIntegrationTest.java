package com.example.springsoap;

import static org.assertj.core.api.Assertions.*;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryIntegrationTest {
  @Autowired InventoryService service;
  @Autowired JdbcTemplate db;
  @Autowired TestRestTemplate http;
  @LocalServerPort int port;

  @BeforeEach
  void clean() {
    db.update("DELETE FROM stock_movements");
    db.update("DELETE FROM order_lines");
    db.update("DELETE FROM customer_orders");
    db.update("DELETE FROM products");
  }

  Product product(String sku, int quantity) {
    return service.create(
        new NewProduct(sku, "Test " + sku, "Tests", new BigDecimal("12.35"), quantity, 3));
  }

  NewOrder request(String sku, int quantity) {
    return new NewOrder("Test customer", List.of(new LineRequest(sku, quantity)));
  }

  ResponseEntity<String> soap(String body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_XML);
    return http.postForEntity(
        "/ws",
        new HttpEntity<>(
            "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'"
                + " xmlns:i='https://stockbridge.example/inventory/v1'><s:Body>"
                + body
                + "</s:Body></s:Envelope>",
            headers),
        String.class);
  }

  @Test
  void totalsAreExactAndStockIsReserved() {
    product("A", 10);
    var o = service.place(request("A", 3));
    assertThat(o.total()).isEqualByComparingTo("37.05");
    assertThat(service.product("A").quantity()).isEqualTo(7);
    assertThat(service.movements()).hasSize(2);
  }

  @Test
  void failedMultiLineOrderRollsBackAllChanges() {
    product("A", 10);
    product("Z", 1);
    assertThatThrownBy(
            () ->
                service.place(
                    new NewOrder(
                        "Customer", List.of(new LineRequest("A", 2), new LineRequest("Z", 2)))))
        .isInstanceOf(BusinessException.class);
    assertThat(service.product("A").quantity()).isEqualTo(10);
    assertThat(service.product("Z").quantity()).isEqualTo(1);
    assertThat(service.orders()).isEmpty();
    assertThat(service.movements()).hasSize(2);
  }

  @Test
  void cancellationIsIdempotent() {
    product("A", 10);
    var o = service.place(request("A", 3));
    service.cancel(o.id());
    service.cancel(o.id());
    assertThat(service.product("A").quantity()).isEqualTo(10);
    assertThat(service.order(o.id()).status()).isEqualTo("CANCELLED");
    assertThat(service.movements()).hasSize(3);
    assertThat(service.summary().activeOrders()).isZero();
  }

  @Test
  void negativeQuantitiesAndFractionalMoneyAreRejected() {
    product("A", 10);
    assertThatThrownBy(() -> service.restock("A", -1)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.place(request("A", 0))).isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                service.create(new NewProduct("B", "Name", "Tests", new BigDecimal("1.001"), 0, 1)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void duplicateSkuAndDuplicateOrderLinesAreRejected() {
    product("A", 10);
    assertThatThrownBy(() -> product("a", 3)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                service.place(
                    new NewOrder(
                        "Customer", List.of(new LineRequest("A", 1), new LineRequest("a", 2)))))
        .isInstanceOf(BusinessException.class);
    assertThat(service.product("A").quantity()).isEqualTo(10);
  }

  @Test
  void concurrentOrdersCannotOversell() throws Exception {
    product("A", 5);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> work =
          () -> {
            start.await();
            try {
              service.place(request("A", 4));
              return true;
            } catch (BusinessException e) {
              assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK");
              return false;
            }
          };
      var a = pool.submit(work);
      var b = pool.submit(work);
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(service.product("A").quantity()).isEqualTo(1);
      assertThat(service.orders()).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void simultaneousCancellationsRestoreStockOnce() throws Exception {
    product("A", 5);
    var order = service.place(request("A", 4));
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Order> work =
          () -> {
            start.await();
            return service.cancel(order.id());
          };
      var a = pool.submit(work);
      var b = pool.submit(work);
      start.countDown();
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
      assertThat(service.product("A").quantity()).isEqualTo(5);
      assertThat(service.movements()).hasSize(3);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void restockAndLowStockSummaryWork() {
    product("A", 2);
    assertThat(service.summary().lowStock()).isEqualTo(1);
    service.restock("A", 5);
    assertThat(service.product("A").quantity()).isEqualTo(7);
    assertThat(service.summary().lowStock()).isZero();
    assertThat(service.summary().inventoryValue()).isEqualByComparingTo("86.45");
  }

  @Test
  void wsdlAndGeneratedSoapEndpointWork() {
    product("A", 10);
    var wsdl = http.getForEntity("/ws/inventory.wsdl", String.class);
    assertThat(wsdl.getStatusCode().value()).isEqualTo(200);
    assertThat(wsdl.getBody()).contains("placeOrder", "cancelOrder");
    var response = soap("<i:getProductRequest><i:sku>A</i:sku></i:getProductRequest>");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("getProductResponse", ">10<");
  }

  @Test
  void soapBusinessFaultHasStableCode() {
    var response = soap("<i:getProductRequest><i:sku>MISSING</i:sku></i:getProductRequest>");
    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).contains("Fault", "NOT_FOUND");
  }

  @Test
  void soapSchemaRejectsMissingRequiredField() {
    var response = soap("<i:getProductRequest/>");
    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).contains("Fault");
  }

  @Test
  void soapCanCreateRestockOrderAndCancel() {
    var created =
        soap(
            "<i:createProductRequest><i:product><i:sku>SOAP</i:sku><i:name>SOAP"
                + " product</i:name><i:category>Tests</i:category><i:price>5.25</i:price><i:quantity>10</i:quantity><i:reorderLevel>2</i:reorderLevel></i:product></i:createProductRequest>");
    assertThat(created.getStatusCode().value()).isEqualTo(200);
    assertThat(
            soap("<i:restockProductRequest><i:sku>SOAP</i:sku><i:quantity>5</i:quantity></i:restockProductRequest>")
                .getStatusCode()
                .value())
        .isEqualTo(200);
    var placed =
        soap(
            "<i:placeOrderRequest><i:customer>SOAP"
                + " buyer</i:customer><i:lines><i:sku>SOAP</i:sku><i:quantity>3</i:quantity></i:lines></i:placeOrderRequest>");
    assertThat(placed.getStatusCode().value()).isEqualTo(200);
    assertThat(placed.getBody()).contains("15.75");
    var id = service.orders().get(0).id();
    assertThat(soap("<i:getOrderRequest><i:id>" + id + "</i:id></i:getOrderRequest>").getBody())
        .contains("CONFIRMED");
    assertThat(
            soap("<i:cancelOrderRequest><i:id>" + id + "</i:id></i:cancelOrderRequest>").getBody())
        .contains("CANCELLED");
    assertThat(service.product("SOAP").quantity()).isEqualTo(15);
  }

  @Test
  void restErrorsHaveUsefulStatusAndNoFractionalUnits() {
    product("A", 2);
    var conflict = http.postForEntity("/api/orders", request("A", 3), String.class);
    assertThat(conflict.getStatusCode().value()).isEqualTo(409);
    assertThat(conflict.getBody()).contains("INSUFFICIENT_STOCK");
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var invalid =
        http.postForEntity(
            "/api/products/A/restock",
            new HttpEntity<>("{\"quantity\":1.5}", headers),
            String.class);
    assertThat(invalid.getStatusCode().value()).isEqualTo(400);
    assertThat(http.getForEntity("/api/products/MISSING", String.class).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void dashboardAndRestFlowWork() {
    assertThat(http.getForEntity("/", String.class).getBody())
        .contains("StockBridge", "SOAP explorer");
    var create =
        http.postForEntity(
            "/api/products",
            new NewProduct("REST", "REST item", "Tests", new BigDecimal("2.50"), 5, 1),
            Product.class);
    assertThat(create.getStatusCode().value()).isEqualTo(201);
    var placed = http.postForEntity("/api/orders", request("REST", 2), Order.class);
    assertThat(placed.getStatusCode().value()).isEqualTo(201);
    var id = placed.getBody().id();
    assertThat(
            http.postForEntity("/api/orders/" + id + "/cancel", Map.of(), Order.class)
                .getBody()
                .status())
        .isEqualTo("CANCELLED");
  }
}
