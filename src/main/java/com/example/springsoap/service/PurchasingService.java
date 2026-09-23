package com.example.springsoap.service;

import com.example.springsoap.model.Models.Product;
import com.example.springsoap.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchasingService {
  public record Supplier(String id, String name, String email) {}

  public record NewSupplier(String name, String email) {}

  public record PurchaseLine(String sku, Integer quantity, BigDecimal unitCost) {}

  public record NewPurchase(String supplierId, List<PurchaseLine> lines) {}

  public record Purchase(
      String id,
      String supplierId,
      String supplierName,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime receivedAt,
      BigDecimal total,
      List<PurchaseLine> lines) {}

  public record Alert(
      String sku,
      String name,
      int quantity,
      int reorderLevel,
      long onOrder,
      long suggestedQuantity) {}

  private final JdbcTemplate db;
  private final InventoryRepository inventory;

  public PurchasingService(JdbcTemplate db, InventoryRepository inventory) {
    this.db = db;
    this.inventory = inventory;
  }

  private void require(boolean ok, String message) {
    if (!ok) throw new BusinessException("INVALID_INPUT", message);
  }

  public List<Supplier> suppliers() {
    return db.query(
        "SELECT * FROM suppliers ORDER BY name,id",
        (r, n) -> new Supplier(r.getString("id"), r.getString("name"), r.getString("email")));
  }

  @Transactional
  public Supplier addSupplier(NewSupplier input) {
    require(
        input.name() != null && !input.name().isBlank() && input.name().trim().length() <= 100,
        "Supplier name is required (maximum 100 characters).");
    require(
        input.email() != null
            && input.email().length() <= 254
            && input.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
        "Enter a valid supplier email.");
    var s = new Supplier(UUID.randomUUID().toString(), input.name().trim(), input.email().trim());
    db.update("INSERT INTO suppliers VALUES (?,?,?)", s.id(), s.name(), s.email());
    return s;
  }

  public Purchase purchase(String id) {
    var found =
        db.query(
            "SELECT p.*,s.name AS supplier_name FROM purchase_orders p JOIN suppliers s ON"
                + " s.id=p.supplier_id WHERE p.id=?",
            (r, n) ->
                new Purchase(
                    r.getString("id"),
                    r.getString("supplier_id"),
                    r.getString("supplier_name"),
                    r.getString("status"),
                    r.getObject("created_at", OffsetDateTime.class),
                    r.getObject("received_at", OffsetDateTime.class),
                    r.getBigDecimal("total"),
                    List.of()),
            id);
    if (found.isEmpty()) throw new BusinessException("NOT_FOUND", "Purchase order not found.");
    var p = found.get(0);
    var lines =
        db.query(
            "SELECT * FROM purchase_lines WHERE purchase_id=? ORDER BY sku",
            (r, n) ->
                new PurchaseLine(
                    r.getString("sku"), r.getInt("quantity"), r.getBigDecimal("unit_cost")),
            id);
    return new Purchase(
        p.id(),
        p.supplierId(),
        p.supplierName(),
        p.status(),
        p.createdAt(),
        p.receivedAt(),
        p.total(),
        lines);
  }

  public List<Purchase> purchases() {
    return db
        .queryForList("SELECT id FROM purchase_orders ORDER BY created_at DESC", String.class)
        .stream()
        .map(this::purchase)
        .toList();
  }

  @Transactional
  public Purchase create(NewPurchase input) {
    require(
        input.supplierId() != null
            && db.queryForObject(
                    "SELECT COUNT(*) FROM suppliers WHERE id=?", Integer.class, input.supplierId())
                == 1,
        "Choose an existing supplier.");
    require(
        input.lines() != null && !input.lines().isEmpty() && input.lines().size() <= 100,
        "A purchase order needs 1 to 100 lines.");
    var lines = new TreeMap<String, PurchaseLine>();
    BigDecimal total = BigDecimal.ZERO;
    for (var line : input.lines()) {
      require(line != null && line.sku() != null, "Product is required.");
      var sku = line.sku().trim().toUpperCase(Locale.ROOT);
      inventory.product(sku, false);
      require(!lines.containsKey(sku), "Combine duplicate products into one line.");
      require(
          line.quantity() != null && line.quantity() > 0 && line.quantity() <= 1_000_000,
          "Quantity must be between 1 and 1000000.");
      require(
          line.unitCost() != null
              && line.unitCost().signum() >= 0
              && line.unitCost().compareTo(new BigDecimal("1000000")) <= 0
              && line.unitCost().stripTrailingZeros().scale() <= 2,
          "Unit cost must be between 0 and 1000000, with at most two decimal places.");
      lines.put(sku, new PurchaseLine(sku, line.quantity(), line.unitCost()));
      total = total.add(line.unitCost().multiply(BigDecimal.valueOf(line.quantity())));
    }
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO purchase_orders(id,supplier_id,status,created_at,total) VALUES"
            + " (?,?,'ORDERED',?,?)",
        id,
        input.supplierId(),
        OffsetDateTime.now(),
        total);
    for (var l : lines.values())
      db.update(
          "INSERT INTO purchase_lines VALUES (?,?,?,?)", id, l.sku(), l.quantity(), l.unitCost());
    return purchase(id);
  }

  private void lock(String id) {
    if (db.queryForList("SELECT id FROM purchase_orders WHERE id=? FOR UPDATE", String.class, id)
        .isEmpty()) throw new BusinessException("NOT_FOUND", "Purchase order not found.");
  }

  @Transactional
  public Purchase receive(String id) {
    lock(id);
    var p = purchase(id);
    if (p.status().equals("RECEIVED")) return p;
    if (!p.status().equals("ORDERED"))
      throw new BusinessException("CONFLICT", "A cancelled purchase order cannot be received.");
    // Sorted product locks and one transaction prevent partial deliveries on failure.
    for (var l : p.lines()) {
      Product product = inventory.product(l.sku(), true);
      require(
          (long) product.quantity() + l.quantity() <= 1_000_000,
          "Receipt exceeds the stock limit for " + l.sku());
      int balance = product.quantity() + l.quantity();
      inventory.stock(l.sku(), balance);
      inventory.movement(l.sku(), l.quantity(), balance, "Purchase receipt " + id);
    }
    db.update(
        "UPDATE purchase_orders SET status='RECEIVED',received_at=? WHERE id=?",
        OffsetDateTime.now(),
        id);
    return purchase(id);
  }

  @Transactional
  public Purchase cancel(String id) {
    lock(id);
    var p = purchase(id);
    if (p.status().equals("RECEIVED"))
      throw new BusinessException("CONFLICT", "A received purchase order cannot be cancelled.");
    db.update("UPDATE purchase_orders SET status='CANCELLED' WHERE id=?", id);
    return purchase(id);
  }

  public List<Alert> alerts() {
    return db.query(
        "SELECT p.*,COALESCE((SELECT SUM(l.quantity) FROM purchase_lines l JOIN purchase_orders o"
            + " ON o.id=l.purchase_id WHERE l.sku=p.sku AND o.status='ORDERED'),0) AS on_order FROM"
            + " products p WHERE p.quantity<=p.reorder_level ORDER BY p.quantity,p.sku",
        (r, n) ->
            new Alert(
                r.getString("sku"),
                r.getString("name"),
                r.getInt("quantity"),
                r.getInt("reorder_level"),
                r.getLong("on_order"),
                Math.max(
                    0,
                    (long) r.getInt("reorder_level")
                        + 1
                        - r.getInt("quantity")
                        - r.getLong("on_order"))));
  }
}
