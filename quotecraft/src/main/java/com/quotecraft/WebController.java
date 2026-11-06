package com.quotecraft;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
  final QuoteService service;
  final PdfService pdf;

  public WebController(QuoteService s, PdfService p) {
    service = s;
    pdf = p;
  }

  long owner(Principal p) {
    return ((Number) service.business(p.getName()).get("id")).longValue();
  }

  void base(Model m, Principal p) {
    m.addAttribute("business", service.business(p.getName()));
  }

  @GetMapping("/")
  String home() {
    return "home";
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/signup")
  String signup() {
    return "signup";
  }

  @PostMapping("/signup")
  String register(
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam String password,
      Model m) {
    try {
      service.register(name, email, password);
      return "redirect:/login?created";
    } catch (IllegalArgumentException e) {
      m.addAttribute("error", e.getMessage());
      m.addAttribute("name", name);
      m.addAttribute("email", email);
      return "signup";
    }
  }

  @GetMapping("/dashboard")
  String dashboard(Principal p, @RequestParam(defaultValue = "") String search, Model m) {
    base(m, p);
    var all = service.list(owner(p), "");
    m.addAttribute("quotes", service.list(owner(p), search));
    m.addAttribute("search", search);
    m.addAttribute("count", all.size());
    m.addAttribute(
        "pending",
        all.stream()
            .filter(q -> q.get("status").equals("SENT") && !QuoteService.expired(q))
            .count());
    m.addAttribute(
        "approved",
        all.stream()
            .filter(q -> q.get("status").equals("ACCEPTED"))
            .map(q -> (BigDecimal) q.get("total"))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    m.addAttribute(
        "active",
        all.stream()
            .filter(
                q -> q.get("status").equals("ACCEPTED") && !q.get("job_stage").equals("COMPLETED"))
            .count());
    return "dashboard";
  }

  @GetMapping("/quotes/new")
  String form(Principal p, Model m) {
    base(m, p);
    m.addAttribute(
        "values",
        Map.of(
            "expires", QuoteService.today().plusDays(14).toString(), "discount", "0", "tax", "0"));
    m.addAttribute(
        "lines",
        List.of(Map.of("description", "Standard home cleaning", "quantity", "1", "price", "120")));
    return "form";
  }

  @PostMapping("/quotes")
  String create(
      Principal p,
      @RequestParam Map<String, String> v,
      @RequestParam List<String> description,
      @RequestParam List<String> quantity,
      @RequestParam List<String> price,
      Model m) {
    try {
      long id =
          service.create(
              owner(p),
              v.get("customer"),
              v.get("email"),
              v.get("address"),
              v.get("title"),
              v.get("notes"),
              v.get("discount"),
              v.get("tax"),
              v.get("expires"),
              description,
              quantity,
              price);
      return "redirect:/quotes/" + id;
    } catch (IllegalArgumentException e) {
      base(m, p);
      m.addAttribute("error", e.getMessage());
      m.addAttribute("values", v);
      var lines = new ArrayList<Map<String, String>>();
      for (int i = 0; i < Math.min(description.size(), 20); i++)
        lines.add(
            Map.of(
                "description",
                description.get(i),
                "quantity",
                i < quantity.size() ? quantity.get(i) : "",
                "price",
                i < price.size() ? price.get(i) : ""));
      m.addAttribute("lines", lines);
      return "form";
    }
  }

  @GetMapping("/quotes/{id}/revise")
  String revise(Principal p, @PathVariable long id, Model m) {
    base(m, p);
    var q = service.own(owner(p), id);
    q.put("expires", QuoteService.today().plusDays(14));
    m.addAttribute("values", q);
    m.addAttribute("lines", service.lines(id));
    m.addAttribute("revision", true);
    return "form";
  }

  @GetMapping("/quotes/{id}")
  String detail(Principal p, @PathVariable long id, Model m) {
    base(m, p);
    var q = service.own(owner(p), id);
    m.addAttribute("q", q);
    m.addAttribute("lines", service.lines(id));
    m.addAttribute("expired", QuoteService.expired(q));
    m.addAttribute("today", QuoteService.today());
    return "detail";
  }

  @PostMapping("/quotes/{id}/share")
  String share(Principal p, @PathVariable long id, RedirectAttributes r) {
    try {
      service.share(owner(p), id);
      r.addFlashAttribute(
          "notice", "Review link created. Copy it and share it with your customer.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/quotes/" + id;
  }

  @PostMapping("/quotes/{id}/withdraw")
  String withdraw(Principal p, @PathVariable long id, RedirectAttributes r) {
    try {
      service.withdraw(owner(p), id);
      r.addFlashAttribute("notice", "Quote withdrawn. Its customer link is no longer valid.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/quotes/" + id;
  }

  @PostMapping("/quotes/{id}/schedule")
  String schedule(
      Principal p, @PathVariable long id, @RequestParam String date, RedirectAttributes r) {
    try {
      service.schedule(owner(p), id, date);
      r.addFlashAttribute("notice", "Job scheduled.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/quotes/" + id;
  }

  @PostMapping("/quotes/{id}/advance")
  String advance(Principal p, @PathVariable long id, RedirectAttributes r) {
    try {
      service.advance(owner(p), id);
      r.addFlashAttribute("notice", "Job progress updated.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/quotes/" + id;
  }

  @GetMapping("/jobs")
  String jobs(Principal p, Model m) {
    base(m, p);
    m.addAttribute(
        "quotes",
        service.list(owner(p), "").stream()
            .filter(q -> q.get("status").equals("ACCEPTED"))
            .toList());
    m.addAttribute("stages", List.of("UNSCHEDULED", "SCHEDULED", "IN_PROGRESS", "COMPLETED"));
    return "jobs";
  }

  @GetMapping("/review/{token}")
  String review(@PathVariable String token, Model m) {
    var q = service.review(token);
    m.addAttribute("q", q);
    m.addAttribute("lines", service.lines(((Number) q.get("id")).longValue()));
    m.addAttribute("expired", QuoteService.expired(q));
    return "review";
  }

  @PostMapping("/review/{token}")
  String respond(
      @PathVariable String token,
      @RequestParam String action,
      @RequestParam String name,
      @RequestParam(defaultValue = "") String feedback,
      RedirectAttributes r) {
    try {
      service.respond(token, action, name, feedback);
      r.addFlashAttribute("notice", "Your response has been recorded. Thank you.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/review/" + token;
  }

  @GetMapping("/quotes/{id}/pdf")
  ResponseEntity<byte[]> pdf(Principal p, @PathVariable long id) throws Exception {
    var q = service.own(owner(p), id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header("Content-Disposition", "attachment; filename=QuoteCraft-" + id + ".pdf")
        .body(pdf.render(q, service.lines(id), (String) service.business(p.getName()).get("name")));
  }
}
