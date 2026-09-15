package com.example.springsoap.web;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.InventoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping("/products")
  public List<Product> products() {
    return service.products();
  }

  @GetMapping("/products/{sku}")
  public Product product(@PathVariable String sku) {
    return service.product(sku);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public Product create(@RequestBody NewProduct p) {
    return service.create(p);
  }

  @PostMapping("/products/{sku}/restock")
  public Product restock(@PathVariable String sku, @RequestBody Restock r) {
    return service.restock(sku, r.quantity());
  }

  @GetMapping("/orders")
  public List<Order> orders() {
    return service.orders();
  }

  @GetMapping("/orders/{id}")
  public Order order(@PathVariable String id) {
    return service.order(id);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Order place(@RequestBody NewOrder o) {
    return service.place(o);
  }

  @PostMapping("/orders/{id}/cancel")
  public Order cancel(@PathVariable String id) {
    return service.cancel(id);
  }

  @GetMapping("/movements")
  public List<Movement> movements() {
    return service.movements();
  }

  @GetMapping("/summary")
  public Summary summary() {
    return service.summary();
  }
}
