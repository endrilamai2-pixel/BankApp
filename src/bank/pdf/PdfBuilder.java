package bank.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Απλός δημιουργός PDF χωρίς εξωτερικές βιβλιοθήκες. Ενσωματώνει γραμματοσειρά TrueType,
 * ώστε να εμφανίζονται σωστά τα ελληνικά. Υποστηρίζει κείμενο με αναδίπλωση, πίνακες και αλλαγή σελίδας.
 */
public final class PdfBuilder {

    public enum Align { LEFT, CENTER, RIGHT }

    public static final float PAGE_W = 595.28f;
    public static final float PAGE_H = 841.89f;
    private static final float MARGIN = 56f;
    private static final float BOTTOM = MARGIN + 14f;
    private static final float[] BLACK = {0f, 0f, 0f};
    private static final float[] GRAY = {0.4f, 0.4f, 0.4f};

    private final TtfFont regular;
    private final TtfFont bold;
    private final String title;
    private final String footerLeft;
    private final Map<Integer, Integer> usedRegular = new TreeMap<>();
    private final Map<Integer, Integer> usedBold = new TreeMap<>();
    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder cur;
    private float y;

    public PdfBuilder(TtfFont regular, TtfFont bold, String title, String footerLeft) {
        this.regular = regular;
        this.bold = bold != null ? bold : regular;
        this.title = title;
        this.footerLeft = footerLeft;
        newPage();
    }

    // ===================================================== Διάταξη

    public float contentWidth() { return PAGE_W - 2 * MARGIN; }

    public void space(float h) {
        y -= h;
        if (y < BOTTOM) newPage();
    }

    /** Αν δεν χωράνε h points στη σελίδα, ξεκινά νέα σελίδα (ώστε να μην αποχωρίζονται οι τίτλοι από το κείμενο). */
    public void ensureSpace(float h) {
        if (y - h < BOTTOM) newPage();
    }

    public void newPage() {
        cur = new StringBuilder();
        pages.add(cur);
        y = PAGE_H - MARGIN;
    }

    public void paragraph(String text, boolean isBold, float size, Align align, float indent, boolean gray) {
        TtfFont f = isBold ? bold : regular;
        float width = contentWidth() - indent;
        float lh = size * 1.4f;
        for (String line : wrap(text, f, size, width)) {
            if (y - lh < BOTTOM) newPage();
            float x = MARGIN + indent;
            float tw = f.width(line, size);
            if (align == Align.CENTER) x += (width - tw) / 2;
            else if (align == Align.RIGHT) x += width - tw;
            drawText(cur, isBold, size, x, y - size, line, gray ? GRAY : BLACK);
            y -= lh;
        }
    }

    public void title(String text) {
        paragraph(text, true, 18, Align.CENTER, 0, false);
    }

    public void heading(String text) {
        space(10);
        if (y - 40 < BOTTOM) newPage();     // να μην μένει τίτλος μόνος στο τέλος σελίδας
        paragraph(text, true, 12, Align.LEFT, 0, false);
        space(2);
    }

    public void rule() {
        space(4);
        cur.append("0.6 G 0.5 w ").append(n(MARGIN)).append(' ').append(n(y)).append(" m ")
           .append(n(PAGE_W - MARGIN)).append(' ').append(n(y)).append(" l S\n");
        space(6);
    }

    /** Πίνακας δύο στηλών «ετικέτα - τιμή». */
    public void keyValueTable(List<String[]> rows) {
        float labelW = 160f;
        float[] widths = {labelW, contentWidth() - labelW};
        Align[] aligns = {Align.LEFT, Align.LEFT};
        for (String[] row : rows) {
            ensureRow(row, widths, null, aligns);
            drawRow(row, widths, aligns, new boolean[]{true, false}, true);
        }
        space(6);
    }

    /** Γενικός πίνακας με επικεφαλίδα που επαναλαμβάνεται σε κάθε σελίδα. */
    public void table(String[] headers, List<String[]> rows, float[] widths, Align[] aligns) {
        boolean[] boldHeader = new boolean[headers.length];
        java.util.Arrays.fill(boldHeader, true);
        ensureRow(headers, widths, null, aligns);
        drawRow(headers, widths, aligns, boldHeader, true);
        for (String[] row : rows) {
            if (rowHeight(row, widths) > y - BOTTOM) {
                newPage();
                drawRow(headers, widths, aligns, boldHeader, true);
            }
            drawRow(row, widths, aligns, new boolean[headers.length], false);
        }
        space(6);
    }

    /** Δύο γραμμές υπογραφής δίπλα-δίπλα. */
    public void signatures(String left, String right) {
        if (y - 90 < BOTTOM) newPage();
        space(40);
        float half = contentWidth() / 2;
        float lineY = y;
        cur.append("0.3 G 0.7 w ")
           .append(n(MARGIN + 10)).append(' ').append(n(lineY)).append(" m ").append(n(MARGIN + half - 30)).append(' ').append(n(lineY)).append(" l S\n")
           .append(n(MARGIN + half + 30)).append(' ').append(n(lineY)).append(" m ").append(n(PAGE_W - MARGIN - 10)).append(' ').append(n(lineY)).append(" l S\n");
        float lw = bold.width(left, 10), rw = bold.width(right, 10);
        drawText(cur, true, 10, MARGIN + 10 + (half - 40 - lw) / 2, lineY - 14, left, BLACK);
        drawText(cur, true, 10, MARGIN + half + 30 + (half - 40 - rw) / 2, lineY - 14, right, BLACK);
        y = lineY - 24;
    }

    // ===================================================== Εσωτερικά: πίνακες

    private static final float PAD = 4f;
    private static final float TABLE_SIZE = 9.5f;
    private static final float TABLE_LH = TABLE_SIZE * 1.35f;

    private float rowHeight(String[] cells, float[] widths) {
        int lines = 1;
        for (int i = 0; i < cells.length; i++) {
            lines = Math.max(lines, wrap(cells[i], regular, TABLE_SIZE, widths[i] - 2 * PAD).size());
        }
        return lines * TABLE_LH + 2 * PAD - 2;
    }

    private void ensureRow(String[] cells, float[] widths, boolean[] unused, Align[] aligns) {
        if (rowHeight(cells, widths) > y - BOTTOM) newPage();
    }

    private void drawRow(String[] cells, float[] widths, Align[] aligns, boolean[] boldCols, boolean shade) {
        float h = rowHeight(cells, widths);
        float x = MARGIN;
        float top = y;
        for (int i = 0; i < cells.length; i++) {
            TtfFont f = boldCols[i] ? bold : regular;
            if (shade) {
                cur.append("0.94 g ").append(n(x)).append(' ').append(n(top - h)).append(' ')
                   .append(n(widths[i])).append(' ').append(n(h)).append(" re f\n");
            }
            cur.append("0.7 G 0.5 w ").append(n(x)).append(' ').append(n(top - h)).append(' ')
               .append(n(widths[i])).append(' ').append(n(h)).append(" re S\n");
            float ty = top - PAD - TABLE_SIZE + 1;
            for (String line : wrap(cells[i], f, TABLE_SIZE, widths[i] - 2 * PAD)) {
                float tx = x + PAD;
                float tw = f.width(line, TABLE_SIZE);
                if (aligns[i] == Align.RIGHT) tx = x + widths[i] - PAD - tw;
                else if (aligns[i] == Align.CENTER) tx = x + (widths[i] - tw) / 2;
                drawText(cur, boldCols[i], TABLE_SIZE, tx, ty, line, BLACK);
                ty -= TABLE_LH;
            }
            x += widths[i];
        }
        y = top - h;
    }

    // ===================================================== Εσωτερικά: κείμενο

    private List<String> wrap(String text, TtfFont f, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String t = text == null ? "" : text;
        for (String para : t.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (f.width(candidate, size) <= maxWidth) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                // πολύ μεγάλη λέξη: σπάει σε κομμάτια
                String rest = word;
                while (f.width(rest, size) > maxWidth && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && f.width(rest.substring(0, cut), size) > maxWidth) cut--;
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line = new StringBuilder(rest);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void drawText(StringBuilder sb, boolean isBold, float size, float x, float yy, String s, float[] rgb) {
        TtfFont f = isBold ? bold : regular;
        Map<Integer, Integer> used = f == bold && bold != regular ? usedBold : usedRegular;
        sb.append("BT ").append(n(rgb[0])).append(' ').append(n(rgb[1])).append(' ').append(n(rgb[2])).append(" rg /F")
          .append(f == bold && bold != regular ? 2 : 1).append(' ').append(n(size)).append(" Tf ")
          .append(n(x)).append(' ').append(n(yy)).append(" Td <").append(hex(f, used, s)).append("> Tj ET\n");
    }

    private static String hex(TtfFont f, Map<Integer, Integer> used, String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 32) cp = 32;
            int gid = f.glyph(cp);
            if (gid == 0) {
                cp = '?';
                gid = f.glyph(cp);
            }
            used.putIfAbsent(gid, cp);
            sb.append(String.format("%04X", gid));
        }
        return sb.toString();
    }

    private static String n(float v) { return String.format(Locale.ROOT, "%.2f", v); }

    // ===================================================== Δημιουργία αρχείου

    public byte[] build() throws IOException {
        // Υποσέλιδα (χρειάζεται ο συνολικός αριθμός σελίδων)
        int total = pages.size();
        for (int i = 0; i < total; i++) {
            StringBuilder sb = pages.get(i);
            sb.append("0.8 G 0.5 w ").append(n(MARGIN)).append(" 42 m ").append(n(PAGE_W - MARGIN)).append(" 42 l S\n");
            drawText(sb, false, 8, MARGIN, 30, footerLeft, GRAY);
            String pg = "Σελίδα " + (i + 1) + " από " + total;
            drawText(sb, false, 8, PAGE_W - MARGIN - regular.width(pg, 8), 30, pg, GRAY);
        }

        boolean twoFonts = bold != regular;
        int fontCount = twoFonts ? 2 : 1;
        int firstPageObj = 3 + 5 * fontCount;
        int infoObj = firstPageObj + 2 * total;
        int lastObj = infoObj;
        byte[][] bodies = new byte[lastObj + 1][];

        // 1: Catalog, 2: Pages
        bodies[1] = ascii("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < total; i++) kids.append(firstPageObj + 2 * i).append(" 0 R ");
        bodies[2] = ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + total + " >>");

        // Γραμματοσειρές
        writeFont(bodies, 3, regular, usedRegular);
        if (twoFonts) writeFont(bodies, 8, bold, usedBold);
        String fontRes = "/F1 3 0 R" + (twoFonts ? " /F2 8 0 R" : "");

        // Σελίδες
        for (int i = 0; i < total; i++) {
            int pageObj = firstPageObj + 2 * i, contentObj = pageObj + 1;
            bodies[pageObj] = ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + n(PAGE_W) + " " + n(PAGE_H)
                    + "] /Resources << /Font << " + fontRes + " >> >> /Contents " + contentObj + " 0 R >>");
            bodies[contentObj] = stream("", pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1));
        }
        bodies[infoObj] = ascii("<< /Title <" + utf16Hex(title) + "> /Producer (Bank App) >>");

        // Εγγραφή αρχείου
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.7\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes(StandardCharsets.ISO_8859_1));
        long[] offsets = new long[lastObj + 1];
        for (int id = 1; id <= lastObj; id++) {
            offsets[id] = out.size();
            out.write((id + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(bodies[id]);
            out.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        long xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (lastObj + 1) + "\n0000000000 65535 f \n");
        for (int id = 1; id <= lastObj; id++) x.append(String.format("%010d 00000 n \n", offsets[id]));
        x.append("trailer\n<< /Size ").append(lastObj + 1).append(" /Root 1 0 R /Info ").append(infoObj)
         .append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.write(x.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    /** Γράφει τα 5 αντικείμενα μιας ενσωματωμένης γραμματοσειράς, ξεκινώντας από το id «base». */
    private void writeFont(byte[][] bodies, int base, TtfFont f, Map<Integer, Integer> used) throws IOException {
        int type0 = base, cid = base + 1, desc = base + 2, file = base + 3, toUni = base + 4;
        String name = f.baseName().isEmpty() ? "EmbeddedFont" : f.baseName();

        bodies[type0] = ascii("<< /Type /Font /Subtype /Type0 /BaseFont /" + name + " /Encoding /Identity-H"
                + " /DescendantFonts [" + cid + " 0 R] /ToUnicode " + toUni + " 0 R >>");

        StringBuilder w = new StringBuilder();
        for (int gid : used.keySet()) w.append(gid).append(" [").append(f.width1000(gid)).append("] ");
        bodies[cid] = ascii("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /" + name
                + " /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>"
                + " /FontDescriptor " + desc + " 0 R /DW 1000 /W [" + w + "] /CIDToGIDMap /Identity >>");

        bodies[desc] = ascii("<< /Type /FontDescriptor /FontName /" + name + " /Flags 32 /FontBBox ["
                + f.xMin1000() + " " + f.yMin1000() + " " + f.xMax1000() + " " + f.yMax1000() + "] /ItalicAngle 0"
                + " /Ascent " + f.ascent1000() + " /Descent " + f.descent1000()
                + " /CapHeight " + (int) (f.ascent1000() * 0.7) + " /StemV 80 /FontFile2 " + file + " 0 R >>");

        bodies[file] = stream("/Length1 " + f.data().length, f.data());

        StringBuilder cm = new StringBuilder();
        cm.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
          .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
          .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
          .append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(used.entrySet());
        for (int i = 0; i < entries.size(); i += 100) {
            int end = Math.min(i + 100, entries.size());
            cm.append(end - i).append(" beginbfchar\n");
            for (int k = i; k < end; k++) {
                cm.append(String.format("<%04X> <%04X>\n", entries.get(k).getKey(), entries.get(k).getValue()));
            }
            cm.append("endbfchar\n");
        }
        cm.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
        bodies[toUni] = stream("", cm.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] stream(String extraDict, byte[] raw) throws IOException {
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(z, new Deflater(Deflater.BEST_COMPRESSION))) {
            d.write(raw);
        }
        byte[] comp = z.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("<< /Length " + comp.length + " /Filter /FlateDecode " + extraDict + " >>\nstream\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(comp);
        out.write("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

    private static String utf16Hex(String s) {
        StringBuilder sb = new StringBuilder("FEFF");
        for (char c : s.toCharArray()) sb.append(String.format("%04X", (int) c));
        return sb.toString();
    }
}
