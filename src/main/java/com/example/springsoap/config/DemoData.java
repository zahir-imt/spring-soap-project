package com.example.springsoap.config;

import com.example.springsoap.model.Models.*;
import com.example.springsoap.service.InventoryService;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class DemoData {
  @Bean
  @ConditionalOnProperty(name = "stockbridge.seed", havingValue = "true")
  ApplicationRunner seed(InventoryService s) {
    return args -> {
      if (!s.products().isEmpty()) return;
      s.create(
          new NewProduct(
              "KB-101", "Mechanical keyboard", "Peripherals", new BigDecimal("89.00"), 42, 10));
      s.create(
          new NewProduct(
              "MS-202", "Wireless mouse", "Peripherals", new BigDecimal("35.50"), 8, 10));
      s.create(
          new NewProduct("MN-303", "27-inch monitor", "Displays", new BigDecimal("249.00"), 16, 5));
      s.create(new NewProduct("HB-404", "USB-C hub", "Accessories", new BigDecimal("49.00"), 5, 8));
      s.create(
          new NewProduct("WC-505", "HD webcam", "Peripherals", new BigDecimal("69.00"), 23, 6));
      s.create(
          new NewProduct("LP-606", "Laptop stand", "Accessories", new BigDecimal("39.00"), 0, 5));
    };
  }
}
