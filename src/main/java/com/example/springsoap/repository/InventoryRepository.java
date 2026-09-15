package com.example.springsoap.repository;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.BusinessException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
  private final JdbcTemplate db;

  public InventoryRepository(JdbcTemplate db) {
    this.db = db;
  }

  private final RowMapper<Product> productMapper =
      (r, n) ->
          new Product(
              r.getString("sku"),
              r.getString("name"),
              r.getString("category"),
              r.getBigDecimal("price"),
              r.getInt("quantity"),
              r.getInt("reorder_level"));

  public List<Product> products() {
    return db.query("SELECT * FROM products ORDER BY sku", productMapper);
  }

  public Product product(String sku, boolean lock) {
    var found =
        db.query(
            "SELECT * FROM products WHERE sku=?" + (lock ? " FOR UPDATE" : ""), productMapper, sku);
    if (found.isEmpty()) throw new BusinessException("NOT_FOUND", "Product not found: " + sku);
    return found.get(0);
  }

  public void insert(Product p) {
    db.update(
        "INSERT INTO products VALUES (?,?,?,?,?,?)",
        p.sku(),
        p.name(),
        p.category(),
        p.price(),
        p.quantity(),
        p.reorderLevel());
  }

  public void stock(String sku, int quantity) {
    db.update("UPDATE products SET quantity=? WHERE sku=?", quantity, sku);
  }

  public void movement(String sku, int delta, int balance, String reason) {
    db.update(
        "INSERT INTO stock_movements(sku,delta,balance,reason,created_at) VALUES (?,?,?,?,?)",
        sku,
        delta,
        balance,
        reason,
        OffsetDateTime.now());
  }

  public List<Movement> movements() {
    return db.query(
        "SELECT * FROM stock_movements ORDER BY id DESC LIMIT 100",
        (r, n) ->
            new Movement(
                r.getLong("id"),
                r.getString("sku"),
                r.getInt("delta"),
                r.getInt("balance"),
                r.getString("reason"),
                r.getObject("created_at", OffsetDateTime.class)));
  }

  public void insertOrder(String id, String customer, BigDecimal total, List<OrderLine> lines) {
    db.update(
        "INSERT INTO customer_orders VALUES (?,?,?,?,?)",
        id,
        customer,
        "CONFIRMED",
        total,
        OffsetDateTime.now());
    for (var l : lines)
      db.update(
          "INSERT INTO order_lines(order_id,sku,name,quantity,unit_price) VALUES (?,?,?,?,?)",
          id,
          l.sku(),
          l.name(),
          l.quantity(),
          l.unitPrice());
  }

  public Order order(String id, boolean lock) {
    var found =
        db.query(
            "SELECT * FROM customer_orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Order(
                    r.getString("id"),
                    r.getString("customer"),
                    r.getString("status"),
                    r.getBigDecimal("total"),
                    r.getObject("created_at", OffsetDateTime.class),
                    List.of()),
            id);
    if (found.isEmpty()) throw new BusinessException("NOT_FOUND", "Order not found: " + id);
    var o = found.get(0);
    var lines =
        db.query(
            "SELECT * FROM order_lines WHERE order_id=? ORDER BY sku",
            (r, n) ->
                new OrderLine(
                    r.getString("sku"),
                    r.getString("name"),
                    r.getInt("quantity"),
                    r.getBigDecimal("unit_price")),
            id);
    return new Order(o.id(), o.customer(), o.status(), o.total(), o.createdAt(), lines);
  }

  public List<Order> orders() {
    return db
        .queryForList("SELECT id FROM customer_orders ORDER BY created_at DESC", String.class)
        .stream()
        .map(id -> order(id, false))
        .toList();
  }

  public void cancel(String id) {
    db.update("UPDATE customer_orders SET status='CANCELLED' WHERE id=?", id);
  }
}
