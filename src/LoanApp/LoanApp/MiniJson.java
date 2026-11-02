package LoanApp.LoanApp;


    import java.util.*;

    /**
     * Ελάχιστος αναλυτής JSON χωρίς εξωτερικές βιβλιοθήκες (η Java δεν έχει ενσωματωμένο).
     * Αντικείμενα -> Map, πίνακες -> List, αριθμοί -> Double, και String / Boolean / null.
     */
    final class MiniJson {
        private final String s;
        private int i = 0;

        private MiniJson(String s) { this.s = s; }

        static Object parse(String text) {
            try {
                MiniJson p = new MiniJson(text);
                return p.value();
            } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                throw new IllegalArgumentException("Μη έγκυρο JSON", e);
            }
        }

        // ---------- Αναζήτηση μέσα σε δέντρο JSON (ανεκτική σε άγνωστη δομή) ----------

        /** Πρώτο αντικείμενο (σε βάθος) που περιέχει οποιοδήποτε από τα κλειδιά (σύγκριση χωρίς πεζά/κεφαλαία). */
        @SuppressWarnings("unchecked")
        static Map<String, Object> firstObjectWithAnyKey(Object node, Set<String> lowerKeys) {
            if (node instanceof Map<?, ?> m) {
                for (Object k : m.keySet()) {
                    if (lowerKeys.contains(String.valueOf(k).toLowerCase())) return (Map<String, Object>) m;
                }
                for (Object v : m.values()) {
                    Map<String, Object> r = firstObjectWithAnyKey(v, lowerKeys);
                    if (r != null) return r;
                }
            } else if (node instanceof List<?> l) {
                for (Object v : l) {
                    Map<String, Object> r = firstObjectWithAnyKey(v, lowerKeys);
                    if (r != null) return r;
                }
            }
            return null;
        }

        /** Πρώτη τιμή-κείμενο/αριθμός για οποιοδήποτε από τα κλειδιά, ή null. */
        static String find(Object node, Set<String> lowerKeys) {
            if (node instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object v = e.getValue();
                    if (!lowerKeys.contains(String.valueOf(e.getKey()).toLowerCase())) continue;
                    if (v instanceof String || v instanceof Number) {
                        return v instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(v);
                    }
                    if (v instanceof Map<?, ?> || v instanceof List<?>) {   // π.χ. "legalType": {"descr": "Ανώνυμη Εταιρεία"}
                        String inner = firstText(v);
                        if (inner != null) return inner;
                    }
                }
                for (Object v : m.values()) {
                    String r = find(v, lowerKeys);
                    if (r != null) return r;
                }
            } else if (node instanceof List<?> l) {
                for (Object v : l) {
                    String r = find(v, lowerKeys);
                    if (r != null) return r;
                }
            }
            return null;
        }

        /** Το πρώτο κείμενο (String) που βρίσκεται οπουδήποτε μέσα στον κόμβο. */
        private static String firstText(Object node) {
            if (node instanceof String str) return str;
            if (node instanceof Map<?, ?> m) {
                for (Object v : m.values()) {
                    String r = firstText(v);
                    if (r != null) return r;
                }
            } else if (node instanceof List<?> l) {
                for (Object v : l) {
                    String r = firstText(v);
                    if (r != null) return r;
                }
            }
            return null;
        }

        // ---------- Συντακτικός αναλυτής ----------

        private Object value() {
            ws();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> { i += 4; yield Boolean.TRUE; }
                case 'f' -> { i += 5; yield Boolean.FALSE; }
                case 'n' -> { i += 4; yield null; }
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;                                   // {
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Αναμενόταν , ή }");
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;                                   // [
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Αναμενόταν , ή ]");
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);   // \" \\ \/
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("Αναμενόταν " + c + " στη θέση " + i);
            i++;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }


