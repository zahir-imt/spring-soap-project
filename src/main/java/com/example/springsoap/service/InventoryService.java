package com.example.springsoap.service;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.repository.InventoryRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {
  private final InventoryRepository repo;

  public InventoryService(InventoryRepository repo) {
    this.repo = repo;
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new BusinessException("INVALID_INPUT", message);
  }

  private static String text(String value, String field, int max) {
    require(value != null && !value.isBlank(), field + " is required.");
    String v = value.trim();
    require(v.length() <= max, field + " must be at most " + max + " characters.");
    return v;
  }

  private static String sku(String s) {
    String v = text(s, "SKU", 32).toUpperCase(Locale.ROOT);
    require(
        v.matches("[A-Z0-9][A-Z0-9_-]*"), "SKU must use letters, numbers, hyphens or underscores.");
    return v;
  }

  private static int number(Integer n, String field, int min) {
    require(
        n != null && n >= min && n <= 1_000_000,
        field + " must be between " + min + " and 1000000.");
    return n;
  }

  public List<Product> products() {
    return repo.products();
  }

  public Product product(String key) {
    return repo.product(sku(key), false);
  }

  public List<Order> orders() {
    return repo.orders();
  }

  public Order order(String id) {
    return repo.order(text(id, "Order ID", 36), false);
  }

  public List<Movement> movements() {
    return repo.movements();
  }

  public Summary summary() {
    var ps = products();
    var os = orders().stream().filter(o -> o.status().equals("CONFIRMED")).toList();
    return new Summary(
        ps.size(),
        ps.stream().mapToLong(Product::quantity).sum(),
        ps.stream().filter(p -> p.quantity() <= p.reorderLevel()).count(),
        ps.stream()
            .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        os.size(),
        os.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Transactional
  public Product create(NewProduct n) {
    require(n != null, "Product is required.");
    String key = sku(n.sku()),
        name = text(n.name(), "Name", 100),
        category = text(n.category(), "Category", 60);
    require(
        n.price() != null
            && n.price().signum() >= 0
            && n.price().compareTo(new BigDecimal("1000000")) <= 0,
        "Price must be between 0 and 1000000.");
    require(
        n.price().stripTrailingZeros().scale() <= 2, "Price must have at most two decimal places.");
    Product p =
        new Product(
            key,
            name,
            category,
            n.price(),
            number(n.quantity(), "Stock", 0),
            number(n.reorderLevel(), "Reorder level", 0));
    try {
      repo.insert(p);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException("DUPLICATE_SKU", "This SKU already exists.");
    }
    repo.movement(key, p.quantity(), p.quantity(), "Opening stock");
    return p;
  }

  @Transactional
  public Product restock(String key, Integer amount) {
    int add = number(amount, "Restock quantity", 1);
    Product p = repo.product(sku(key), true);
    require((long) p.quantity() + add <= 1_000_000, "Total stock cannot exceed 1000000.");
    repo.stock(p.sku(), p.quantity() + add);
    repo.movement(p.sku(), add, p.quantity() + add, "Restock");
    return repo.product(p.sku(), false);
  }

  @Transactional
  public Order place(NewOrder request) {
    require(request != null, "Order is required.");
    String customer = text(request.customer(), "Customer", 100);
    require(
        request.lines() != null && !request.lines().isEmpty() && request.lines().size() <= 100,
        "An order must contain 1 to 100 lines.");
    // Lock products in SKU order so competing orders cannot oversell or lock in opposite orders.
    var quantities = new TreeMap<String, Integer>();
    for (var l : request.lines()) {
      require(l != null, "Order line is required.");
      String key = sku(l.sku());
      require(!quantities.containsKey(key), "Combine duplicate SKUs into one line.");
      quantities.put(key, number(l.quantity(), "Order quantity", 1));
    }
    var lines = new ArrayList<OrderLine>();
    BigDecimal total = BigDecimal.ZERO;
    String id = UUID.randomUUID().toString();
    for (var entry : quantities.entrySet()) {
      var p = repo.product(entry.getKey(), true);
      int count = entry.getValue();
      if (p.quantity() < count)
        throw new BusinessException(
            "INSUFFICIENT_STOCK",
            "Not enough stock for " + p.sku() + ". Available: " + p.quantity());
      repo.stock(p.sku(), p.quantity() - count);
      repo.movement(p.sku(), -count, p.quantity() - count, "Order " + id);
      lines.add(new OrderLine(p.sku(), p.name(), count, p.price()));
      total = total.add(p.price().multiply(BigDecimal.valueOf(count)));
    }
    repo.insertOrder(id, customer, total, lines);
    return repo.order(id, false);
  }

  @Transactional
  public Order cancel(String id) {
    var o = repo.order(text(id, "Order ID", 36), true);
    if (o.status().equals("CANCELLED")) return o;
    for (var l : o.lines()) {
      var p = repo.product(l.sku(), true);
      require(
          (long) p.quantity() + l.quantity() <= 1_000_000,
          "Cancellation would exceed the stock limit for " + p.sku());
      repo.stock(p.sku(), p.quantity() + l.quantity());
      repo.movement(p.sku(), l.quantity(), p.quantity() + l.quantity(), "Cancellation " + id);
    }
    repo.cancel(id);
    return repo.order(id, false);
  }
}
