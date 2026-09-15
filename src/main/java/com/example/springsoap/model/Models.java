package com.example.springsoap.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class Models {
  private Models() {}

  public record Product(
      String sku, String name, String category, BigDecimal price, int quantity, int reorderLevel) {}

  public record NewProduct(
      String sku,
      String name,
      String category,
      BigDecimal price,
      Integer quantity,
      Integer reorderLevel) {}

  public record LineRequest(String sku, Integer quantity) {}

  public record NewOrder(String customer, List<LineRequest> lines) {}

  public record OrderLine(String sku, String name, int quantity, BigDecimal unitPrice) {}

  public record Order(
      String id,
      String customer,
      String status,
      BigDecimal total,
      OffsetDateTime createdAt,
      List<OrderLine> lines) {}

  public record Movement(
      long id, String sku, int delta, int balance, String reason, OffsetDateTime createdAt) {}

  public record Restock(Integer quantity) {}

  public record Summary(
      int products,
      long units,
      long lowStock,
      BigDecimal inventoryValue,
      int activeOrders,
      BigDecimal orderValue) {}
}
