package com.example.springsoap;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.*;
import com.example.springsoap.service.PurchasingService.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpgradeIntegrationTest {
  @Autowired InventoryService inventory;
  @Autowired PurchasingService purchasing;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc http;

  @BeforeEach
  void clean() {
    for (String table :
        List.of(
            "order_requests",
            "purchase_lines",
            "purchase_orders",
            "suppliers",
            "stock_movements",
            "order_lines",
            "customer_orders",
            "products")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM app_users WHERE username<>'admin'");
  }

  void product(String sku, int qty) {
    inventory.create(new NewProduct(sku, sku, "Tests", new BigDecimal("10.00"), qty, 5));
  }

  NewOrder order(String key, int qty) {
    return new NewOrder("Buyer", List.of(new LineRequest("A", qty)), key);
  }

  Purchase purchase(List<PurchaseLine> lines) {
    var s = purchasing.addSupplier(new NewSupplier("Supplier", "supplier@example.com"));
    return purchasing.create(new NewPurchase(s.id(), lines));
  }

  PurchaseLine line(String sku, int qty) {
    return new PurchaseLine(sku, qty, new BigDecimal("2.35"));
  }

  @Test
  void repeatedOrderReservesOnceAndRejectsChangedPayload() {
    product("A", 10);
    var first = inventory.place(order("same", 2));
    assertThat(inventory.place(order("same", 2)).id()).isEqualTo(first.id());
    assertThat(inventory.product("A").quantity()).isEqualTo(8);
    assertThatThrownBy(() -> inventory.place(order("same", 3)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("different order");
    inventory.cancel(first.id());
    assertThat(inventory.place(order("same", 2)).status()).isEqualTo("CANCELLED");
    assertThat(inventory.product("A").quantity()).isEqualTo(10);
  }

  @Test
  void failedOrderCanBeRetriedWithSameKey() {
    product("A", 1);
    assertThatThrownBy(() -> inventory.place(order("retry", 2)))
        .isInstanceOf(BusinessException.class);
    inventory.restock("A", 3);
    inventory.place(order("retry", 2));
    assertThat(inventory.product("A").quantity()).isEqualTo(2);
  }

  @Test
  void simultaneousDuplicateSubmissionsCreateOneOrder() throws Exception {
    product("A", 10);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Order> task =
          () -> {
            start.await();
            return inventory.place(order("parallel", 3));
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS).id()).isEqualTo(b.get(15, TimeUnit.SECONDS).id());
      assertThat(inventory.orders()).hasSize(1);
      assertThat(inventory.product("A").quantity()).isEqualTo(7);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void purchaseAndAlertsTrackIncomingAndReceivedStock() {
    product("A", 1);
    assertThat(purchasing.alerts().get(0).suggestedQuantity()).isEqualTo(5);
    var p = purchase(List.of(line("A", 5)));
    assertThat(p.total()).isEqualByComparingTo("11.75");
    assertThat(inventory.product("A").quantity()).isEqualTo(1);
    assertThat(purchasing.alerts().get(0).onOrder()).isEqualTo(5);
    assertThat(purchasing.alerts().get(0).suggestedQuantity()).isZero();
    purchasing.receive(p.id());
    purchasing.receive(p.id());
    assertThat(inventory.product("A").quantity()).isEqualTo(6);
    assertThat(purchasing.alerts()).isEmpty();
    assertThat(inventory.movements()).hasSize(2);
    assertThat(purchasing.purchase(p.id()).receivedAt()).isNotNull();
    assertThatThrownBy(() -> purchasing.cancel(p.id())).isInstanceOf(BusinessException.class);
  }

  @Test
  void cancelledPurchaseCannotBeReceivedAndRestoresSuggestion() {
    product("A", 1);
    var p = purchase(List.of(line("A", 5)));
    purchasing.cancel(p.id());
    purchasing.cancel(p.id());
    assertThat(purchasing.alerts().get(0).suggestedQuantity()).isEqualTo(5);
    assertThatThrownBy(() -> purchasing.receive(p.id())).isInstanceOf(BusinessException.class);
    assertThat(inventory.product("A").quantity()).isEqualTo(1);
  }

  @Test
  void failedReceiptRollsBackEveryLine() {
    product("A", 1);
    product("Z", 1_000_000);
    var p = purchase(List.of(line("A", 5), line("Z", 1)));
    assertThatThrownBy(() -> purchasing.receive(p.id())).isInstanceOf(BusinessException.class);
    assertThat(inventory.product("A").quantity()).isEqualTo(1);
    assertThat(purchasing.purchase(p.id()).status()).isEqualTo("ORDERED");
    assertThat(inventory.movements()).hasSize(2);
  }

  @Test
  void simultaneousReceiptsAddStockOnce() throws Exception {
    product("A", 1);
    var p = purchase(List.of(line("A", 5)));
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Purchase> work =
          () -> {
            start.await();
            return purchasing.receive(p.id());
          };
      var a = pool.submit(work);
      var b = pool.submit(work);
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS).status()).isEqualTo("RECEIVED");
      b.get(15, TimeUnit.SECONDS);
      assertThat(inventory.product("A").quantity()).isEqualTo(6);
      assertThat(inventory.movements()).hasSize(2);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void purchaseValidationRejectsBadCostsAndDuplicateLines() {
    product("A", 1);
    var s = purchasing.addSupplier(new NewSupplier("Supplier", "hello@example.com"));
    for (var lines :
        List.of(
            List.of(line("A", 0)),
            List.of(line("A", 1), line("a", 2)),
            List.of(new PurchaseLine("A", 1, new BigDecimal("1.001")))))
      assertThatThrownBy(() -> purchasing.create(new NewPurchase(s.id(), lines)))
          .isInstanceOf(BusinessException.class);
    assertThat(purchasing.purchases()).isEmpty();
  }

  @Test
  void authenticationCsrfAndLoginAreEnforced() throws Exception {
    http.perform(get("/api/products")).andExpect(status().isUnauthorized());
    http.perform(
            post("/api/products/A/restock")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"quantity\":1}"))
        .andExpect(status().isForbidden());
    http.perform(post("/login").with(csrf()).param("username", "admin").param("password", "wrong"))
        .andExpect(redirectedUrl("/login.html?error"));
    http.perform(
            post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "Test-password-123"))
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void rolesProtectMutationsAndAdminCreatesHashedAccounts() throws Exception {
    product("A", 10);
    http.perform(
            post("/api/products/A/restock")
                .with(user("sales").roles("SALES"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"quantity\":1}"))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/orders")
                .with(user("warehouse").roles("WAREHOUSE"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"customer\":\"Buyer\",\"requestId\":\"x\",\"lines\":[{\"sku\":\"A\",\"quantity\":1}]}"))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/suppliers")
                .with(user("sales").roles("SALES"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"S\",\"email\":\"a@b.com\"}"))
        .andExpect(status().isForbidden());
    http.perform(get("/api/users").with(user("warehouse").roles("WAREHOUSE")))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/users")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"username\":\"newstaff\",\"password\":\"Staff-password-123\",\"role\":\"WAREHOUSE\"}"))
        .andExpect(status().isCreated());
    assertThat(
            db.queryForObject(
                "SELECT password FROM app_users WHERE username='newstaff'", String.class))
        .startsWith("$2")
        .doesNotContain("Staff-password");
    http.perform(
            post("/login")
                .with(csrf())
                .param("username", "newstaff")
                .param("password", "Staff-password-123"))
        .andExpect(redirectedUrl("/"));
    assertThat(inventory.product("A").quantity()).isEqualTo(10);
  }

  @Test
  void restOrderRequiresIdAndReplaysSameResult() throws Exception {
    product("A", 10);
    String body =
        "{\"customer\":\"Buyer\",\"requestId\":\"rest-key\",\"lines\":[{\"sku\":\"A\",\"quantity\":2}]}";
    for (int i = 0; i < 2; i++)
      http.perform(
              post("/api/orders")
                  .with(user("sales").roles("SALES"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isCreated());
    http.perform(
            post("/api/orders")
                .with(user("sales").roles("SALES"))
                .with(csrf())
                .contentType("application/json")
                .content(body.replace("\"requestId\":\"rest-key\",", "")))
        .andExpect(status().isBadRequest());
    assertThat(inventory.orders()).hasSize(1);
    assertThat(inventory.product("A").quantity()).isEqualTo(8);
  }
}
