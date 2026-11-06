package com.quotecraft;

import java.awt.Color;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

@Service
public class PdfService {
  private static final Color GREEN = new Color(21, 59, 53);

  private record Paragraph(String text, boolean strong, float size) {}

  public byte[] render(Map<String, Object> q, List<Map<String, Object>> lines, String business)
      throws IOException {
    var text = new ArrayList<Paragraph>();
    text.add(new Paragraph(business, true, 22));
    text.add(new Paragraph("QUOTE QC-" + q.get("id") + "  /  " + q.get("status"), true, 11));
    text.add(new Paragraph(q.get("title").toString(), true, 17));
    text.add(new Paragraph("Prepared for: " + q.get("customer"), false, 11));
    text.add(new Paragraph("Email: " + q.get("email"), false, 11));
    text.add(new Paragraph("Service address: " + q.get("address"), false, 11));
    text.add(new Paragraph("Valid through: " + q.get("expires") + " (Toronto time)", false, 11));
    text.add(new Paragraph("SERVICES / ALL AMOUNTS IN CAD", true, 11));
    for (var l : lines) {
      text.add(new Paragraph(l.get("description").toString(), true, 12));
      text.add(
          new Paragraph(
              l.get("quantity") + " x $" + l.get("price") + " = $" + l.get("amount"), false, 11));
    }
    text.add(new Paragraph("Subtotal: $" + q.get("subtotal"), false, 11));
    text.add(new Paragraph("Discount: $" + q.get("discount"), false, 11));
    text.add(new Paragraph("Tax rate: " + q.get("tax") + "% (after discount)", false, 11));
    text.add(new Paragraph("TOTAL CAD: $" + q.get("total"), true, 18));
    text.add(new Paragraph("SCOPE & TERMS", true, 11));
    text.add(new Paragraph(q.get("notes").toString(), false, 11));
    if (!q.get("reviewer").toString().isEmpty()) {
      text.add(new Paragraph("CUSTOMER RESPONSE / " + q.get("reviewer"), true, 11));
      text.add(new Paragraph(q.get("feedback").toString(), false, 11));
    }
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      PDPageContentStream stream = null;
      float y = 0;
      var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      try {
        for (var p : text) {
          var font = p.strong ? bold : regular;
          String safe = sanitize(p.text, font);
          for (String line : wrap(safe, font, p.size, 510)) {
            if (stream == null || y < p.size + 62) {
              if (stream != null) stream.close();
              var page = new PDPage();
              doc.addPage(page);
              stream = new PDPageContentStream(doc, page);
              stream.setNonStrokingColor(GREEN);
              stream.addRect(0, 740, 612, 52);
              stream.fill();
              write(stream, bold, 12, 45, 760, "QUOTECRAFT / A CLEARER WAY TO WORK", Color.WHITE);
              write(
                  stream,
                  regular,
                  9,
                  45,
                  30,
                  "QC-" + q.get("id") + "  |  Page " + doc.getNumberOfPages() + "  |  CAD",
                  GREEN);
              y = 705;
            }
            write(stream, font, p.size, 45, y, line, GREEN);
            y -= p.size + 7;
          }
          y -= 8;
        }
      } finally {
        if (stream != null) stream.close();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private static void write(
      PDPageContentStream s, PDFont f, float size, float x, float y, String text, Color color)
      throws IOException {
    s.setNonStrokingColor(color);
    s.beginText();
    s.setFont(f, size);
    s.newLineAtOffset(x, y);
    s.showText(text);
    s.endText();
  }

  private static String sanitize(String text, PDFont font) {
    var b = new StringBuilder();
    text.codePoints()
        .forEach(
            cp -> {
              String ch = new String(Character.toChars(cp));
              if (cp == 10 || cp == 13) {
                b.append(' ');
                return;
              }
              try {
                font.encode(ch);
                b.append(ch);
              } catch (Exception e) {
                b.append('?');
              }
            });
    return b.toString();
  }

  private static List<String> wrap(String text, PDFont font, float size, float width)
      throws IOException {
    var result = new ArrayList<String>();
    var line = new StringBuilder();
    for (char c : text.toCharArray()) {
      if (font.getStringWidth(line.toString() + c) / 1000 * size > width) {
        result.add(line.toString());
        line.setLength(0);
      }
      line.append(c);
    }
    result.add(line.toString());
    return result;
  }
}
