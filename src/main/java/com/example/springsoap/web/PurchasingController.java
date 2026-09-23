package com.example.springsoap.web;

import com.example.springsoap.service.PurchasingService;
import com.example.springsoap.service.PurchasingService.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PurchasingController {
  private final PurchasingService service;

  public PurchasingController(PurchasingService service) {
    this.service = service;
  }

  @GetMapping("/alerts")
  public List<Alert> alerts() {
    return service.alerts();
  }

  @GetMapping("/suppliers")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public List<Supplier> suppliers() {
    return service.suppliers();
  }

  @PostMapping("/suppliers")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  @ResponseStatus(HttpStatus.CREATED)
  public Supplier supplier(@RequestBody NewSupplier input) {
    return service.addSupplier(input);
  }

  @GetMapping("/purchases")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public List<Purchase> purchases() {
    return service.purchases();
  }

  @PostMapping("/purchases")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  @ResponseStatus(HttpStatus.CREATED)
  public Purchase create(@RequestBody NewPurchase input) {
    return service.create(input);
  }

  @PostMapping("/purchases/{id}/receive")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public Purchase receive(@PathVariable String id) {
    return service.receive(id);
  }

  @PostMapping("/purchases/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
  public Purchase cancel(@PathVariable String id) {
    return service.cancel(id);
  }
}
