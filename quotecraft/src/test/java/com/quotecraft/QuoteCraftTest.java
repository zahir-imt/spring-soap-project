package com.quotecraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:qc;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password="
    })
@AutoConfigureMockMvc
class QuoteCraftTest {
  @Autowired QuoteService s;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder passwords;
  @Autowired PdfService pdf;
  long owner, other;

  @BeforeEach
  void setup() {
    db.update("delete from quote_lines");
    db.update("delete from quotes");
    db.update("delete from businesses");
    s.register("Fresh & Bright", "owner@example.test", "Example-password-123");
    s.register("Other Business", "other@example.test", "Other-password-123");
    owner = ((Number) s.business("owner@example.test").get("id")).longValue();
    other = ((Number) s.business("other@example.test").get("id")).longValue();
  }

  long quote() {
    return s.create(
        owner,
        "Sample Customer",
        "customer@example.test",
        "123 Example Street",
        "Deep clean",
        "Bring supplies",
        "10",
        "13",
        QuoteService.today().plusDays(14).toString(),
        List.of("Cleaning", "Windows"),
        List.of("2", "1"),
        List.of("100", "50"));
  }

  String shared(long id) {
    s.share(owner, id);
    return (String) s.own(owner, id).get("token");
  }

  @Test
  void calculatesDiscountBeforeTax() {
    var q = s.own(owner, quote());
    assertEquals(new BigDecimal("250.00"), q.get("subtotal"));
    assertEquals(new BigDecimal("271.20"), q.get("total"));
  }

  @Test
  void roundsLinesHalfUp() {
    long id =
        s.create(
            owner,
            "A",
            "a@example.test",
            "Street",
            "Quote",
            "",
            "0",
            "0",
            QuoteService.today().toString(),
            List.of("Item"),
            List.of("0.15"),
            List.of("0.10"));
    assertEquals(new BigDecimal("0.02"), s.own(owner, id).get("total"));
  }

  @Test
  void rejectsInvalidAmounts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            s.create(
                owner,
                "A",
                "a@example.test",
                "Street",
                "Q",
                "",
                "999",
                "0",
                QuoteService.today().toString(),
                List.of("Item"),
                List.of("1"),
                List.of("10")));
    assertEquals(0, s.list(owner, "").size());
  }

  @Test
  void rejectsEmptyLines() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            s.create(
                owner,
                "A",
                "a@example.test",
                "Street",
                "Q",
                "",
                "0",
                "0",
                QuoteService.today().toString(),
                List.of(),
                List.of(),
                List.of()));
  }

  @Test
  void rejectsInvalidEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> s.register("Business", "not-email", "Example-password-123"));
  }

  @Test
  void passwordsAreHashed() {
    String hash =
        db.queryForObject("select password from businesses where id=?", String.class, owner);
    assertNotEquals("Example-password-123", hash);
    assertTrue(passwords.matches("Example-password-123", hash));
  }

  @Test
  void duplicateEmailIsCaseInsensitive() {
    assertThrows(
        IllegalArgumentException.class,
        () -> s.register("Another", "OWNER@example.test", "Example-password-123"));
  }

  @Test
  void weakPasswordRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> s.register("Another", "new@example.test", "short"));
  }

  @Test
  void tenantsCannotReadOrShare() {
    long id = quote();
    assertThrows(ResponseStatusException.class, () -> s.own(other, id));
    assertThrows(ResponseStatusException.class, () -> s.share(other, id));
    assertTrue(s.list(other, "").isEmpty());
  }

  @Test
  void publicTokenIsUnpredictable() {
    String token = shared(quote());
    assertEquals(64, token.length());
    assertThrows(ResponseStatusException.class, () -> s.review("1"));
    assertEquals("SENT", s.review(token).get("status"));
  }

  @Test
  void acceptanceIsSingleUse() {
    long id = quote();
    String token = shared(id);
    s.respond(token, "accept", "Customer", "");
    assertEquals("ACCEPTED", s.own(owner, id).get("status"));
    assertThrows(IllegalArgumentException.class, () -> s.respond(token, "decline", "Customer", ""));
  }

  @Test
  void changesRequireExplanation() {
    String token = shared(quote());
    assertThrows(IllegalArgumentException.class, () -> s.respond(token, "changes", "Customer", ""));
    s.respond(token, "changes", "Customer", "Please include the garage");
    assertEquals("CHANGES_REQUESTED", s.review(token).get("status"));
  }

  @Test
  void expiredQuoteCannotBeApproved() {
    long id = quote();
    String token = shared(id);
    db.update("update quotes set expires=? where id=?", QuoteService.today().minusDays(1), id);
    assertThrows(IllegalArgumentException.class, () -> s.respond(token, "accept", "Customer", ""));
  }

  @Test
  void withdrawRevokesLink() {
    long id = quote();
    String token = shared(id);
    s.withdraw(owner, id);
    assertThrows(ResponseStatusException.class, () -> s.review(token));
  }

  @Test
  void draftCannotBeScheduled() {
    long id = quote();
    assertThrows(
        IllegalArgumentException.class,
        () -> s.schedule(owner, id, QuoteService.today().toString()));
  }

  @Test
  void acceptedJobProgressesInOrder() {
    long id = quote();
    s.respond(shared(id), "accept", "Customer", "");
    assertThrows(IllegalArgumentException.class, () -> s.advance(owner, id));
    s.schedule(owner, id, QuoteService.today().plusDays(1).toString());
    s.advance(owner, id);
    assertEquals("IN_PROGRESS", s.own(owner, id).get("job_stage"));
    s.advance(owner, id);
    assertEquals("COMPLETED", s.own(owner, id).get("job_stage"));
    assertThrows(IllegalArgumentException.class, () -> s.advance(owner, id));
  }

  @Test
  void onlyOneConcurrentResponseWins() throws Exception {
    String token = shared(quote());
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      Callable<Boolean> call =
          () -> {
            gate.await();
            try {
              s.respond(token, "accept", "Customer", "");
              return true;
            } catch (IllegalArgumentException e) {
              return false;
            }
          };
      var a = pool.submit(call);
      var b = pool.submit(call);
      gate.countDown();
      assertNotEquals(a.get(), b.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void requiresAuthenticationAndCsrf() throws Exception {
    mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/quotes").with(user("owner@example.test"))).andExpect(status().isForbidden());
  }

  @Test
  void rendersEveryOwnerPage() throws Exception {
    long id = quote();
    for (String path :
        List.of("/dashboard", "/jobs", "/quotes/new", "/quotes/" + id, "/quotes/" + id + "/revise"))
      mvc.perform(get(path).with(user("owner@example.test"))).andExpect(status().isOk());
  }

  @Test
  void foreignQuotePdfDenied() throws Exception {
    mvc.perform(get("/quotes/" + quote() + "/pdf").with(user("other@example.test")))
        .andExpect(status().isNotFound());
  }

  @Test
  void publicReviewRendersAndRequiresCsrf() throws Exception {
    String t = shared(quote());
    mvc.perform(get("/review/" + t)).andExpect(status().isOk());
    mvc.perform(post("/review/" + t).param("action", "accept").param("name", "Customer"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/review/" + t).with(csrf()).param("action", "accept").param("name", "Customer"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void pdfContainsPricesAndCustomer() throws Exception {
    long id = quote();
    byte[] bytes = pdf.render(s.own(owner, id), s.lines(id), "Fresh & Bright");
    try (var doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
      assertTrue(text.contains("271.20"));
      assertTrue(text.contains("Sample Customer"));
    }
  }

  @Test
  void xssIsEscapedInReview() throws Exception {
    long id = quote();
    db.update("update quotes set notes=? where id=?", "<script>alert(1)</script>", id);
    mvc.perform(get("/review/" + shared(id)))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));
  }

  @Test
  void customerHistorySearchIsScoped() {
    quote();
    assertEquals(1, s.list(owner, "customer@example.test").size());
    assertEquals(0, s.list(other, "customer@example.test").size());
  }
}
