package com.example.springsoap.web;

import com.example.springsoap.service.BusinessException;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public AccountController(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "token",
        token.getToken(),
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Authentication auth) {
    return Map.of(
        "username",
        auth.getName(),
        "roles",
        auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList());
  }

  public record NewUser(String username, String password, String role) {}

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public List<Map<String, Object>> users() {
    return db.queryForList("SELECT username,role FROM app_users ORDER BY username");
  }

  @PostMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, String> add(@RequestBody NewUser user) {
    if (user.username() == null
        || !user.username().matches("[a-zA-Z0-9._-]{3,50}")
        || user.password() == null
        || user.password().length() < 12
        || user.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || user.role() == null
        || !Set.of("ADMIN", "WAREHOUSE", "SALES").contains(user.role()))
      throw new BusinessException(
          "INVALID_INPUT",
          "Use a 3–50 character username, a 12–72 byte password, and a valid role.");
    try {
      db.update(
          "INSERT INTO app_users VALUES (?,?,?)",
          user.username(),
          encoder.encode(user.password()),
          user.role());
    } catch (DuplicateKeyException e) {
      throw new BusinessException("CONFLICT", "Username already exists.");
    }
    return Map.of("username", user.username(), "role", user.role());
  }
}
