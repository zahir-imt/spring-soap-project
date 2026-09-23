package com.example.springsoap;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Base64;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** Real Basic authentication plus CSRF/session exchange for the existing HTTP regression tests. */
public final class AuthenticatedHttp {
  public static void configure(TestRestTemplate http) {
    if (!http.getRestTemplate().getInterceptors().isEmpty()) return;
    http.getRestTemplate()
        .getInterceptors()
        .add(
            (request, body, execution) -> {
              String auth =
                  "Basic "
                      + Base64.getEncoder().encodeToString("admin:Test-password-123".getBytes());
              request.getHeaders().set("Authorization", auth);
              if (!request.getMethod().name().equals("GET")) {
                try {
                  URI base = request.getURI().resolve("/api/csrf");
                  var response =
                      HttpClient.newHttpClient()
                          .send(
                              HttpRequest.newBuilder(base)
                                  .header("Authorization", auth)
                                  .GET()
                                  .build(),
                              HttpResponse.BodyHandlers.ofString());
                  var token = new ObjectMapper().readTree(response.body());
                  request
                      .getHeaders()
                      .set(token.get("headerName").asText(), token.get("token").asText());
                  response
                      .headers()
                      .firstValue("set-cookie")
                      .ifPresent(c -> request.getHeaders().set("Cookie", c.split(";", 2)[0]));
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new java.io.IOException(e);
                }
              }
              return execution.execute(request, body);
            });
  }
}
