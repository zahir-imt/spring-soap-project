package com.quotecraft;

import java.math.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuoteService {
  final JdbcTemplate db;
  final PasswordEncoder passwords;

  public QuoteService(JdbcTemplate db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Toronto"));
  }

  static String text(String s, int max, String label) {
    s = s == null ? "" : s.trim();
    if (s.isEmpty() || s.length() > max)
      throw new IllegalArgumentException(label + " is required (maximum " + max + " characters).");
    return s;
  }

  static String email(String s) {
    s = text(s, 254, "Email").toLowerCase(Locale.ROOT);
    if (!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Enter a valid email address.");
    return s;
  }

  public void register(String name, String email, String password) {
    name = text(name, 120, "Business name");
    email = email(email);
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Use at least 12 characters and at most 72 UTF-8 bytes for your password.");
    try {
      db.update(
          "insert into businesses(name,email,password) values(?,?,?)",
          name,
          email,
          passwords.encode(password));
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new IllegalArgumentException("This email is already registered. Sign in instead.");
    }
  }

  public Map<String, Object> business(String email) {
    return db.queryForMap("select id,name,email from businesses where email=?", email);
  }

  public List<Map<String, Object>> list(long owner, String search) {
    String q = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
    return db.queryForList(
        "select * from quotes where owner_id=? and (lower(customer) like ? or lower(email) like ?"
            + " or lower(title) like ?) order by created desc,id desc",
        owner,
        q,
        q,
        q);
  }

  public Map<String, Object> own(long owner, long id) {
    var rows = db.queryForList("select * from quotes where id=? and owner_id=?", id, owner);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  public List<Map<String, Object>> lines(long id) {
    return db.queryForList("select * from quote_lines where quote_id=? order by id", id);
  }

  static BigDecimal money(String s, String label, BigDecimal max) {
    try {
      BigDecimal v = new BigDecimal(s);
      if (v.signum() < 0 || v.compareTo(max) > 0 || v.scale() > 2)
        throw new NumberFormatException();
      return v.setScale(2);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          label + " is out of range or has more than two decimal places.");
    }
  }

  @Transactional
  public long create(
      long owner,
      String customer,
      String email,
      String address,
      String title,
      String notes,
      String discount,
      String tax,
      String expires,
      List<String> descriptions,
      List<String> quantities,
      List<String> prices) {
    customer = text(customer, 120, "Customer");
    email = email(email);
    address = text(address, 500, "Service address");
    title = text(title, 160, "Quote title");
    notes = notes == null ? "" : notes.trim();
    if (notes.length() > 2000)
      throw new IllegalArgumentException("Notes must be under 2000 characters.");
    LocalDate expiry;
    try {
      expiry = LocalDate.parse(expires);
    } catch (Exception e) {
      throw new IllegalArgumentException("Choose a valid expiry date.");
    }
    if (expiry.isBefore(today()) || expiry.isAfter(today().plusYears(1)))
      throw new IllegalArgumentException("Expiry must be between today and one year from today.");
    if (descriptions == null
        || descriptions.isEmpty()
        || descriptions.size() > 20
        || quantities == null
        || prices == null
        || descriptions.size() != quantities.size()
        || descriptions.size() != prices.size())
      throw new IllegalArgumentException("Add between 1 and 20 complete service lines.");
    var items = new ArrayList<Object[]>();
    BigDecimal subtotal = BigDecimal.ZERO;
    for (int i = 0; i < descriptions.size(); i++) {
      String d = text(descriptions.get(i), 200, "Service description");
      var qty = money(quantities.get(i), "Quantity", new BigDecimal("9999"));
      var price = money(prices.get(i), "Unit price", new BigDecimal("999999"));
      if (qty.signum() == 0)
        throw new IllegalArgumentException("Quantity must be greater than zero.");
      var amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(amount);
      items.add(new Object[] {d, qty, price, amount});
    }
    if (subtotal.compareTo(new BigDecimal("99999999")) > 0)
      throw new IllegalArgumentException("Quote amount is too large.");
    var disc = money(discount, "Discount", subtotal);
    var rate = money(tax, "Tax rate", new BigDecimal("100"));
    var net = subtotal.subtract(disc);
    var total = net.add(net.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
    var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
    final String c = customer, e = email, a = address, t = title, n = notes;
    final BigDecimal sub = subtotal;
    db.update(
        conn -> {
          var p =
              conn.prepareStatement(
                  "insert into"
                      + " quotes(owner_id,customer,email,address,title,notes,discount,tax,subtotal,total,expires)"
                      + " values(?,?,?,?,?,?,?,?,?,?,?)",
                  new String[] {"id"});
          p.setLong(1, owner);
          p.setString(2, c);
          p.setString(3, e);
          p.setString(4, a);
          p.setString(5, t);
          p.setString(6, n);
          p.setBigDecimal(7, disc);
          p.setBigDecimal(8, rate);
          p.setBigDecimal(9, sub);
          p.setBigDecimal(10, total);
          p.setObject(11, expiry);
          return p;
        },
        key);
    long id = key.getKey().longValue();
    for (var item : items)
      db.update(
          "insert into quote_lines(quote_id,description,quantity,price,amount) values(?,?,?,?,?)",
          id,
          item[0],
          item[1],
          item[2],
          item[3]);
    return id;
  }

  @Transactional
  public void share(long owner, long id) {
    var q = own(owner, id);
    if (expired(q))
      throw new IllegalArgumentException("This quote has expired. Revise it before sharing.");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    if (db.update(
            "update quotes set status='SENT',token=? where id=? and owner_id=? and status='DRAFT'",
            HexFormat.of().formatHex(bytes),
            id,
            owner)
        != 1) throw new IllegalArgumentException("Only drafts can be shared.");
  }

  public static boolean expired(Map<String, Object> q) {
    return ((java.sql.Date) q.get("expires")).toLocalDate().isBefore(today());
  }

  public Map<String, Object> review(String token) {
    if (token == null || !token.matches("[a-f0-9]{64}"))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var rows =
        db.queryForList(
            "select q.*,b.name as business_name from quotes q join businesses b on b.id=q.owner_id"
                + " where q.token=?",
            token);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  @Transactional
  public void respond(String token, String action, String name, String feedback) {
    review(token);
    name = text(name, 120, "Your name");
    feedback = feedback == null ? "" : feedback.trim();
    if (feedback.length() > 2000)
      throw new IllegalArgumentException("Message must be under 2000 characters.");
    String status =
        switch (action) {
          case "accept" -> "ACCEPTED";
          case "changes" -> "CHANGES_REQUESTED";
          case "decline" -> "DECLINED";
          default -> throw new IllegalArgumentException("Choose a valid response.");
        };
    if (status.equals("CHANGES_REQUESTED") && feedback.isEmpty())
      throw new IllegalArgumentException("Describe the changes you need.");
    if (db.update(
            "update quotes set status=?,reviewer=?,feedback=?,responded=CURRENT_TIMESTAMP where"
                + " token=? and status='SENT' and expires>=?",
            status,
            name,
            feedback,
            token,
            today())
        != 1)
      throw new IllegalArgumentException("This quote has expired or already received a response.");
  }

  @Transactional
  public long revise(long owner, long id) {
    var q = own(owner, id);
    var ls = lines(id);
    long fresh =
        create(
            owner,
            (String) q.get("customer"),
            (String) q.get("email"),
            (String) q.get("address"),
            ((String) q.get("title")),
            (String) q.get("notes"),
            q.get("discount").toString(),
            q.get("tax").toString(),
            today().plusDays(14).toString(),
            ls.stream().map(l -> (String) l.get("description")).toList(),
            ls.stream().map(l -> l.get("quantity").toString()).toList(),
            ls.stream().map(l -> l.get("price").toString()).toList());
    return fresh;
  }

  public void withdraw(long owner, long id) {
    own(owner, id);
    if (db.update(
            "update quotes set status='WITHDRAWN',token=null where id=? and owner_id=? and status"
                + " in ('SENT','DRAFT','CHANGES_REQUESTED')",
            id,
            owner)
        != 1) throw new IllegalArgumentException("This quote cannot be withdrawn.");
  }

  public void schedule(long owner, long id, String date) {
    own(owner, id);
    LocalDate d;
    try {
      d = LocalDate.parse(date);
    } catch (Exception e) {
      throw new IllegalArgumentException("Choose a service date.");
    }
    if (d.isBefore(today())) throw new IllegalArgumentException("Choose today or a future date.");
    if (db.update(
            "update quotes set job_stage='SCHEDULED',job_date=? where id=? and owner_id=? and"
                + " status='ACCEPTED' and job_stage in ('UNSCHEDULED','SCHEDULED')",
            d,
            id,
            owner)
        != 1) throw new IllegalArgumentException("Only approved, unstarted jobs can be scheduled.");
  }

  public void advance(long owner, long id) {
    own(owner, id);
    if (db.update(
            "update quotes set job_stage=case when job_stage='SCHEDULED' then 'IN_PROGRESS' else"
                + " 'COMPLETED' end where id=? and owner_id=? and status='ACCEPTED' and job_stage"
                + " in ('SCHEDULED','IN_PROGRESS')",
            id,
            owner)
        != 1) throw new IllegalArgumentException("Schedule an approved job before starting it.");
  }
}
