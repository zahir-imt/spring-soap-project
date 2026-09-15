package com.example.springsoap.bdd;

import static org.assertj.core.api.Assertions.*;

import com.example.springsoap.SpringSoapProjectApplication;
import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.InventoryService;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import io.cucumber.spring.CucumberContextConfiguration;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(
    classes = SpringSoapProjectApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class InventorySteps {
  @Autowired InventoryService service;
  @Autowired JdbcTemplate db;
  @Autowired TestRestTemplate http;
  ResponseEntity<String> response;
  String orderId;

  @Before
  public void clean() {
    db.update("DELETE FROM stock_movements");
    db.update("DELETE FROM order_lines");
    db.update("DELETE FROM customer_orders");
    db.update("DELETE FROM products");
    response = null;
    orderId = null;
  }

  ResponseEntity<String> soap(String body) {
    var h = new HttpHeaders();
    h.setContentType(MediaType.TEXT_XML);
    return http.postForEntity(
        "/ws",
        new HttpEntity<>(
            "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'"
                + " xmlns:i='https://stockbridge.example/inventory/v1'><s:Body>"
                + body
                + "</s:Body></s:Envelope>",
            h),
        String.class);
  }

  @Given("a product with {int} available units")
  public void product(int n) {
    service.create(new NewProduct("BDD", "BDD keyboard", "Tests", new BigDecimal("10.00"), n, 3));
  }

  @When("I order {int} units through SOAP")
  public void order(int n) {
    response =
        soap(
            "<i:placeOrderRequest><i:customer>BDD"
                + " customer</i:customer><i:lines><i:sku>BDD</i:sku><i:quantity>"
                + n
                + "</i:quantity></i:lines></i:placeOrderRequest>");
    if (response.getStatusCode().is2xxSuccessful()) orderId = service.orders().get(0).id();
  }

  @Then("the order is confirmed")
  public void confirmed() {
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("CONFIRMED");
  }

  @Then("the available stock is {int}")
  public void stock(int n) {
    assertThat(service.product("BDD").quantity()).isEqualTo(n);
  }

  @Then("a SOAP fault reports {string}")
  public void fault(String code) {
    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).contains("Fault", code);
  }

  @When("I cancel the order twice through SOAP")
  public void cancel() {
    for (int i = 0; i < 2; i++) {
      response = soap("<i:cancelOrderRequest><i:id>" + orderId + "</i:id></i:cancelOrderRequest>");
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
  }

  @Then("the order is cancelled")
  public void cancelled() {
    assertThat(service.order(orderId).status()).isEqualTo("CANCELLED");
  }

  @When("I restock {int} units through SOAP")
  public void restock(int n) {
    response =
        soap(
            "<i:restockProductRequest><i:sku>BDD</i:sku><i:quantity>"
                + n
                + "</i:quantity></i:restockProductRequest>");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }
}
