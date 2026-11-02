package bank.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ανάγνωση αρχείου γραμματοσειράς TrueType (.ttf): μετρικά και αντιστοίχιση χαρακτήρων σε glyphs. */
public final class TtfFont {

    private final byte[] data;
    private final String baseName;
    private final Map<String, int[]> tables = new HashMap<>();
    private int unitsPerEm, numGlyphs, xMin, yMin, xMax, yMax, ascent, descent;
    private int[] advances;
    private int segCount, endCodeOff, startCodeOff, idDeltaOff, idRangeOffOff;

    private TtfFont(byte[] data, String name) throws IOException {
        this.data = data;
        this.baseName = name.replaceAll("[^A-Za-z0-9-]", "");
        long sfnt = u32(0);
        if (sfnt != 0x00010000L && sfnt != 0x74727565L) {
            throw new IOException("Η γραμματοσειρά πρέπει να είναι TrueType (.ttf): " + name);
        }
        int numTables = u16(4);
        for (int i = 0; i < numTables; i++) {
            int rec = 12 + 16 * i;
            String tag = new String(data, rec, 4, StandardCharsets.ISO_8859_1);
            tables.put(tag, new int[]{(int) u32(rec + 8), (int) u32(rec + 12)});
        }
        int head = table("head");
        unitsPerEm = u16(head + 18);
        xMin = s16(head + 36); yMin = s16(head + 38); xMax = s16(head + 40); yMax = s16(head + 42);
        int hhea = table("hhea");
        ascent = s16(hhea + 4);
        descent = s16(hhea + 6);
        int numHMetrics = u16(hhea + 34);
        numGlyphs = u16(table("maxp") + 4);
        int hmtx = table("hmtx");
        advances = new int[numGlyphs];
        int last = 0;
        for (int i = 0; i < numGlyphs; i++) {
            if (i < numHMetrics) last = u16(hmtx + 4 * i);
            advances[i] = last;
        }
        parseCmap();
    }

    public static TtfFont load(Path path) throws IOException {
        String file = path.getFileName().toString();
        return new TtfFont(Files.readAllBytes(path), file.replaceAll("\\.[^.]+$", ""));
    }

    /** Επιστρέφει την πρώτη γραμματοσειρά της λίστας που υπάρχει, διαβάζεται και περιέχει ελληνικά. */
    public static TtfFont loadFirstWithGreek(List<Path> candidates) {
        for (Path p : candidates) {
            try {
                if (p != null && Files.isRegularFile(p)) {
                    TtfFont f = load(p);
                    if (f.glyph(0x0391) != 0 && f.glyph(0x03B1) != 0) {
                        return f;
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // δοκιμάζουμε την επόμενη
            }
        }
        return null;
    }

    public byte[] data() { return data; }
    public String baseName() { return baseName; }
    public int xMin1000() { return scale(xMin); }
    public int yMin1000() { return scale(yMin); }
    public int xMax1000() { return scale(xMax); }
    public int yMax1000() { return scale(yMax); }
    public int ascent1000() { return scale(ascent); }
    public int descent1000() { return scale(descent); }

    /** Πλάτος glyph σε μονάδες 1/1000 του μεγέθους γραμματοσειράς. */
    public int width1000(int gid) {
        return scale(advances[Math.min(Math.max(gid, 0), numGlyphs - 1)]);
    }

    /** Πλάτος κειμένου σε points. */
    public float width(String text, float size) {
        float w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 32) continue;
            w += width1000(glyph(cp));
        }
        return w * size / 1000f;
    }

    /** Αριθμός glyph για έναν χαρακτήρα Unicode (0 = δεν υπάρχει στη γραμματοσειρά). */
    public int glyph(int cp) {
        if (cp < 0 || cp > 0xFFFF || segCount == 0) return 0;
        int lo = 0, hi = segCount - 1, seg = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (u16(endCodeOff + 2 * mid) >= cp) { seg = mid; hi = mid - 1; } else lo = mid + 1;
        }
        if (seg < 0) return 0;
        int start = u16(startCodeOff + 2 * seg);
        if (cp < start) return 0;
        int delta = u16(idDeltaOff + 2 * seg);
        int rangeOffset = u16(idRangeOffOff + 2 * seg);
        if (rangeOffset == 0) return (cp + delta) & 0xFFFF;
        int p = idRangeOffOff + 2 * seg + rangeOffset + 2 * (cp - start);
        if (p + 1 >= data.length) return 0;
        int g = u16(p);
        return g == 0 ? 0 : (g + delta) & 0xFFFF;
    }

    // ------------------------------------------------------------------

    private void parseCmap() throws IOException {
        int cmap = table("cmap");
        int n = u16(cmap + 2);
        int chosen = -1;
        for (int i = 0; i < n; i++) {
            int rec = cmap + 4 + 8 * i;
            int platform = u16(rec), encoding = u16(rec + 2);
            int off = cmap + (int) u32(rec + 4);
            if (u16(off) != 4) continue;
            if (platform == 3 && encoding == 1) { chosen = off; break; }
            if (platform == 0 && chosen < 0) chosen = off;
        }
        if (chosen < 0) throw new IOException("Η γραμματοσειρά δεν έχει πίνακα Unicode (cmap format 4).");
        segCount = u16(chosen + 6) / 2;
        endCodeOff = chosen + 14;
        startCodeOff = endCodeOff + 2 * segCount + 2;
        idDeltaOff = startCodeOff + 2 * segCount;
        idRangeOffOff = idDeltaOff + 2 * segCount;
    }

    private int table(String tag) throws IOException {
        int[] t = tables.get(tag);
        if (t == null) throw new IOException("Λείπει ο πίνακας '" + tag + "' από τη γραμματοσειρά.");
        return t[0];
    }

    private int scale(int v) { return Math.round(v * 1000f / unitsPerEm); }
    private int u16(int p) { return ((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF); }
    private int s16(int p) { return (short) u16(p); }
    private long u32(int p) { return ((long) u16(p) << 16) | u16(p + 2); }
}
