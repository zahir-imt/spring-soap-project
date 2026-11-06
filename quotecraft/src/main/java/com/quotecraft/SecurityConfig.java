package com.quotecraft;

import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return username -> {
      var rows =
          db.queryForList(
              "select email,password from businesses where email=?",
              username.trim().toLowerCase(Locale.ROOT));
      if (rows.isEmpty()) throw new UsernameNotFoundException("Account not found");
      var b = rows.get(0);
      return User.withUsername((String) b.get("email"))
          .password((String) b.get("password"))
          .roles("OWNER")
          .build();
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity h) throws Exception {
    return h.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/", "/login", "/signup", "/style.css", "/app.js", "/review/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .usernameParameter("email")
                    .defaultSuccessUrl("/dashboard", true)
                    .permitAll())
        .logout(l -> l.logoutSuccessUrl("/"))
        .headers(
            x ->
                x.referrerPolicy(
                    p ->
                        p.policy(
                            org.springframework.security.web.header.writers
                                .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .build();
  }
}
