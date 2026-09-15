package com.example.springsoap.web;

import com.example.springsoap.service.BusinessException;
import java.util.Map;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    int status =
        switch (e.getCode()) {
          case "NOT_FOUND" -> 404;
          case "DUPLICATE_SKU", "INSUFFICIENT_STOCK" -> 409;
          default -> 400;
        };
    return ResponseEntity.status(status)
        .body(Map.of("code", e.getCode(), "message", e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<?> malformed() {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_INPUT",
                "message",
                "Check the request fields and number formats."));
  }

  @ExceptionHandler(ConcurrencyFailureException.class)
  public ResponseEntity<?> busy() {
    return ResponseEntity.status(409)
        .body(Map.of("code", "BUSY", "message", "Stock is being updated. Refresh and try again."));
  }
}
