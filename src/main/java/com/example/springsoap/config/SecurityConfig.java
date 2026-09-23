package com.example.springsoap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return username ->
        db
            .query(
                "SELECT * FROM app_users WHERE username=?",
                (r, n) ->
                    User.withUsername(r.getString("username"))
                        .password(r.getString("password"))
                        .roles(r.getString("role"))
                        .build(),
                username)
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }

  @Bean
  ApplicationRunner bootstrap(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${stockbridge.admin-password:}") String password) {
    return args -> {
      if (db.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class) == 0) {
        if (password.length() < 12
            || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
          throw new IllegalStateException(
              "Set STOCKBRIDGE_ADMIN_PASSWORD to a password of 12–72 characters for first"
                  + " startup.");
        db.update(
            "INSERT INTO app_users VALUES (?,?,?)", "admin", encoder.encode(password), "ADMIN");
      }
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/login.html", "/login.js", "/styles.css", "/api/csrf", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login.html")
                    .loginProcessingUrl("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login.html?error")
                    .permitAll())
        .httpBasic(b -> {})
        .logout(l -> l.logoutSuccessUrl("/login.html"))
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    (req, res, ex) -> res.sendError(401),
                    req ->
                        req.getRequestURI().startsWith("/api/")
                            || req.getRequestURI().startsWith("/ws")));
    return http.build();
  }
}
