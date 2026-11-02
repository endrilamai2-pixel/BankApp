package LoanApp.LoanApp;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Εξάγει τα βασικά μεγέθη από το ΚΕΙΜΕΝΟ οικονομικών καταστάσεων (ισολογισμός / κατάσταση αποτελεσμάτων)
 * που έχουν συνταχθεί με τα υποδείγματα του Παραρτήματος Β του Ν. 4308/2014 (Ελληνικά Λογιστικά Πρότυπα).
 *
 * Πώς δουλεύει: κάθε γραμμή χωρίζεται σε «ετικέτα» και «αριθμούς στο τέλος της γραμμής». Η ετικέτα
 * ταιριάζεται (χωρίς τόνους/πεζά-κεφαλαία) με τις ονομασίες των υποδειγμάτων, π.χ. «Σύνολο ενεργητικού».
 * Από τους αριθμούς της γραμμής χρησιμοποιείται η ΠΡΩΤΗ στήλη ποσών (τρέχουσα χρήση).
 * Για κάθε στοιχείο κρατείται η πρώτη εμφάνισή του στο έγγραφο.
 *
 * Όρια: δεν διαβάζει σκαναρισμένα έγγραφα (χωρίς κείμενο) και δεν «καταλαβαίνει» μη τυποποιημένες ονομασίες.
 * Γι' αυτό το αποτέλεσμα πρέπει πάντα να ελέγχεται από τον χρήστη.
 */
public final class FinancialStatementParser {
    private FinancialStatementParser() {}

    /** Μια τιμή που βρέθηκε, με τη γραμμή του εγγράφου από την οποία προήλθε. */
    public record Extracted(double value, String source, boolean derived) {}

    public record ParseResult(Map<StatementField, Extracted> fields, List<String> warnings) {
        public boolean has(StatementField f) { return fields.containsKey(f); }
    }

    // ===================== ΟΝΟΜΑΣΙΕΣ ΓΡΑΜΜΩΝ (κανονικοποιημένες: πεζά, χωρίς τόνους, με κενά στις άκρες) =====================

    private record Spec(StatementField field, Pattern pattern, Pattern exclude) {}

    private static Spec spec(StatementField f, String regex) { return new Spec(f, Pattern.compile(regex), null); }
    private static Spec spec(StatementField f, String regex, String exclude) {
        return new Spec(f, Pattern.compile(regex), Pattern.compile(exclude));
    }

    /** Η σειρά έχει σημασία: τα πιο συγκεκριμένα πρώτα. */
    private static final List<Spec> SPECS = List.of(
            // Η σειρά «τόκων/φόρων» διαφέρει ανάμεσα στα έγγραφα (το υπόδειγμα λέει «προ τόκων και φόρων», αλλά
            // υπάρχουν και «προ φόρων και τόκων»), γι' αυτό δέχονται και τις δύο.
            spec(StatementField.EBITDA,
                    " ebitda | προ (τοκων φορων|φορων τοκων|τοκων και φορων|φορων και τοκων) (και )?αποσβεσεων "),
            spec(StatementField.EBIT,
                    " (αποτελεσματα|αποτελεσμα|κερδη) προ (τοκων και φορων|φορων και τοκων) ", "αποσβεσεων"),
            // Το «προ φόρων» ΔΕΝ πρέπει να ταιριάζει με γραμμές που αναφέρουν και τόκους (αυτές είναι EBIT)
            spec(StatementField.PROFIT_BEFORE_TAX, " (αποτελεσμα|αποτελεσματα|κερδη|κερδος) προ φορων ", "τοκων"),
            // Το «καθαρά κέρδη/αποτελέσματα» ταιριάζει μόνο όταν είναι ολόκληρη η ετικέτα (όχι «Καθαρά κέρδη ... από χρηματοοικονομικά μέσα»)
            spec(StatementField.NET_PROFIT,
                    " μετα απο φορους | κερδη περιοδου | κερδος περιοδου | αποτελεσμα(τα)? περιοδου |^ καθαρα (κερδη|αποτελεσματα)( χρησης| περιοδου)? $"),
            spec(StatementField.INTEREST_EXPENSE, " χρεωστικοι τοκοι "),
            spec(StatementField.INTEREST_INCOME, " πιστωτικοι τοκοι ", "χρεωστικοι"),
            spec(StatementField.NET_INTEREST, " τοκοι και συναφη κονδυλια "),
            spec(StatementField.NET_TURNOVER, " κυκλος εργασιων "),
            spec(StatementField.TOTAL_ASSETS, " συνολο ενεργητικου "),
            // Εξαιρούνται τα «Σύνολο καθαρής θέσης, προβλέψεων και υποχρεώσεων» / «... και υποχρεώσεων»
            spec(StatementField.EQUITY,
                    " συνολο καθαρης θεσης (?!και |προβλεψεων)| κεφαλαια και αποθεματικα | συνολο ιδιων κεφαλαιων "),
            spec(StatementField.CURRENT_ASSETS,
                    " συνολο κυκλοφορουντων | συνολο κυκλοφορουντος ενεργητικου |(?<!μη) κυκλοφορουν ενεργητικο "),
            spec(StatementField.CURRENT_LIABILITIES, " συνολο βραχυπροθεσμων υποχρεωσεων | βραχυπροθεσμες υποχρεωσεις "),
            spec(StatementField.DEPRECIATION, " αποσβεσεις ", "συσσωρευμ"),
            spec(StatementField.AVG_EMPLOYEES,
                    " μεσος (ορος|αριθμος) (απασχολουμενων|απασχολουμενου προσωπικου|απασχολουμενου|προσωπικου|εργαζομενων) ")
    );

    /** Πεδία στα οποία το πρόσημο εξαρτάται από τον τρόπο παρουσίασης: κρατείται η απόλυτη τιμή. */
    private static final Set<StatementField> ABSOLUTE = EnumSet.of(
            StatementField.INTEREST_EXPENSE, StatementField.INTEREST_INCOME,
            StatementField.NET_INTEREST, StatementField.DEPRECIATION);

    private static final Pattern NUMBER_TOKEN = Pattern.compile("^\\(?[-−–]?\\d[\\d.,]*\\)?-?$|^[-–—]$");
    // Μορφές ποσού: 1.234,56 | 1234,56 | και οι συνηθισμένες παραμορφώσεις του OCR: 1,234,56 και 1.234.56
    private static final Pattern MONEY_TOKEN =
            Pattern.compile("^\\(?[-−–]?\\d{1,3}(\\.\\d{3})+(,\\d+)?\\)?-?$|^\\(?[-−–]?\\d+,\\d+\\)?-?$"
                    + "|^\\(?[-−–]?\\d{1,3}([.,]\\d{3})+[.,]\\d{2}\\)?-?$");

    // ===================== ΚΥΡΙΑ ΜΕΘΟΔΟΣ =====================

    public static ParseResult parse(String text) {
        Map<StatementField, Extracted> found = new EnumMap<>(StatementField.class);
        List<String> warnings = new ArrayList<>();
        String[] lines = text.split("\\R");
        boolean awaitingShortTermTotal = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            Split s = split(line);
            String label = normalize(s.label());
            if (label.isEmpty()) continue;
            String padded = " " + label + " ";
            List<String> nums = s.numbers();

            // Αν η ετικέτα δεν έχει αριθμούς αλλά η επόμενη γραμμή έχει ΜΟΝΟ αριθμούς, ανήκουν σε αυτήν
            if (nums.isEmpty() && i + 1 < lines.length) {
                Split next = split(lines[i + 1].trim());
                if (next.label().isBlank() && !next.numbers().isEmpty()) nums = next.numbers();
            }

            // Στο υπόδειγμα Β.1 οι βραχυπρόθεσμες υποχρεώσεις είναι επικεφαλίδα και το σύνολό τους γραμμή «Σύνολο»
            if (label.equals("βραχυπροθεσμες υποχρεωσεις") && nums.isEmpty()) {
                awaitingShortTermTotal = true;
                continue;
            }
            if (awaitingShortTermTotal) {
                if (label.equals("συνολο") && !nums.isEmpty()) {
                    Double v = pick(nums, StatementField.CURRENT_LIABILITIES);
                    if (v != null) found.putIfAbsent(StatementField.CURRENT_LIABILITIES, new Extracted(v, line, false));
                    awaitingShortTermTotal = false;
                    continue;
                }
                if (label.startsWith("συνολο υποχρεωσεων") || label.contains("μακροπροθεσμες")) {
                    awaitingShortTermTotal = false;
                }
            }

            for (Spec sp : SPECS) {
                if (found.containsKey(sp.field())) continue;                 // κρατείται η πρώτη εμφάνιση
                if (!sp.pattern().matcher(padded).find()) continue;
                if (sp.exclude() != null && sp.exclude().matcher(padded).find()) continue;
                Double v = pick(nums, sp.field());
                if (v == null) continue;                                     // επικεφαλίδα χωρίς ποσά
                found.put(sp.field(), new Extracted(v, line, false));
                break;
            }
        }

        derive(found, warnings);
        sanityChecks(text, found, warnings);
        return new ParseResult(found, warnings);
    }

    // ===================== ΥΠΟΛΟΓΙΖΟΜΕΝΑ ΜΕΓΕΘΗ =====================

    private static void derive(Map<StatementField, Extracted> f, List<String> warnings) {
        // Αν δεν υπάρχουν χωριστά χρεωστικοί τόκοι, χρησιμοποιείται το καθαρό ποσό τόκων (υπόδειγμα Β.6)
        if (!f.containsKey(StatementField.INTEREST_EXPENSE) && f.containsKey(StatementField.NET_INTEREST)) {
            Extracted n = f.get(StatementField.NET_INTEREST);
            f.put(StatementField.INTEREST_EXPENSE, new Extracted(n.value(), n.source(), true));
            warnings.add("Οι χρεωστικοί τόκοι πάρθηκαν από τη γραμμή «Τόκοι και συναφή κονδύλια (καθαρό ποσό)».");
        }

        // Το EBITDA δεν υπάρχει στα υποδείγματα του νόμου: υπολογίζεται όταν υπάρχουν οι αποσβέσεις
        if (!f.containsKey(StatementField.EBITDA) && f.containsKey(StatementField.DEPRECIATION)) {
            double dep = f.get(StatementField.DEPRECIATION).value();
            if (f.containsKey(StatementField.EBIT)) {
                double ebit = f.get(StatementField.EBIT).value();
                f.put(StatementField.EBITDA, new Extracted(ebit + dep, "αποτελέσματα προ τόκων και φόρων + αποσβέσεις", true));
                warnings.add("Το EBITDA υπολογίστηκε ως «Αποτελέσματα προ τόκων και φόρων» + «Αποσβέσεις». Ελέγξτε ότι οι αποσβέσεις αφορούν τη χρήση.");
            } else if (f.containsKey(StatementField.PROFIT_BEFORE_TAX)) {
                double pbt = f.get(StatementField.PROFIT_BEFORE_TAX).value();
                double cost = f.containsKey(StatementField.INTEREST_EXPENSE) ? f.get(StatementField.INTEREST_EXPENSE).value() : 0;
                double income = f.containsKey(StatementField.INTEREST_INCOME) ? f.get(StatementField.INTEREST_INCOME).value() : 0;
                f.put(StatementField.EBITDA, new Extracted(pbt + cost - income + dep,
                        "αποτέλεσμα προ φόρων + τόκοι + αποσβέσεις", true));
                warnings.add("Το EBITDA υπολογίστηκε ως «Αποτέλεσμα προ φόρων» + τόκοι + αποσβέσεις. Ελέγξτε το.");
            }
        }
    }

    // ===================== ΕΛΕΓΧΟΙ ΛΟΓΙΚΗΣ =====================

    private static void sanityChecks(String text, Map<StatementField, Extracted> f, List<String> warnings) {
        String norm = normalize(text);
        String padded = " " + norm + " ";

        // Μονάδα ποσών: οι ενδείξεις «€ Εκατ.» / «€ Χιλ.» επαναλαμβάνονται στις επικεφαλίδες των καταστάσεων
        if (count(padded, " εκατ ") >= 3 || padded.contains(" εκατομμυρια ευρω ")) {
            warnings.add("ΠΡΟΣΟΧΗ: το έγγραφο φαίνεται να έχει ποσά ΣΕ ΕΚΑΤΟΜΜΥΡΙΑ ευρώ (« € Εκατ. »). "
                    + "Οι τιμές που διαβάστηκαν πρέπει να πολλαπλασιαστούν επί 1.000.000.");
        } else if (padded.contains(" χιλιαδες ευρω ") || count(padded, " χιλ ") >= 3) {
            warnings.add("ΠΡΟΣΟΧΗ: το έγγραφο φαίνεται να έχει ποσά ΣΕ ΧΙΛΙΑΔΕΣ ευρώ. "
                    + "Οι τιμές που διαβάστηκαν πρέπει να πολλαπλασιαστούν επί 1.000.");
        }

        // Είδος εγγράφου: ο αναλυτής έχει σχεδιαστεί για ετήσιες, μεμονωμένες καταστάσεις κατά ΕΛΠ
        if (count(padded, " ενδιαμεσ") >= 3) {
            warnings.add("Το έγγραφο είναι ΕΝΔΙΑΜΕΣΟ (π.χ. τρίμηνο): τα αποτελέσματα αφορούν μέρος του έτους "
                    + "και δεν συγκρίνονται με ετήσια.");
        }
        if (count(padded, " ενοποιημεν") >= 3) {
            warnings.add("Οι καταστάσεις είναι ΕΝΟΠΟΙΗΜΕΝΕΣ (όμιλος). Για δάνειο προς συγκεκριμένη εταιρεία "
                    + "χρησιμοποιήστε τις μεμονωμένες καταστάσεις της.");
        }
        if (padded.contains(" υποχρεωσεις προς πιστωτικα ιδρυματα ") && padded.contains(" δανεια και απαιτησεις κατα πελατων ")) {
            warnings.add("ΠΡΟΣΟΧΗ: το έγγραφο φαίνεται να είναι χρηματοπιστωτικού ιδρύματος (τράπεζα, ΔΠΧΑ). "
                    + "Δεν έχει κύκλο εργασιών ούτε κυκλοφορούν/βραχυπρόθεσμα, οπότε το EBITDA και ο βαθμός δεν είναι αξιόπιστα.");
        }

        // Αν οι στήλες ετών είναι με σειρά παλαιότερο -> νεότερο, η «πρώτη στήλη» είναι η προηγούμενη χρήση
        Matcher m = Pattern.compile("(?<!\\d)(20\\d\\d)\\s+(20\\d\\d)(?!\\d)").matcher(text);
        if (m.find() && Integer.parseInt(m.group(1)) < Integer.parseInt(m.group(2))) {
            warnings.add("ΠΡΟΣΟΧΗ: οι στήλες ετών (" + m.group(1) + " " + m.group(2)
                    + ") είναι με σειρά παλαιότερο → νεότερο. Διαβάστηκε η πρώτη στήλη, δηλαδή η παλαιότερη χρήση.");
        }

        Extracted assets = f.get(StatementField.TOTAL_ASSETS);
        if (assets != null) {
            if (f.containsKey(StatementField.EQUITY) && f.get(StatementField.EQUITY).value() > assets.value())
                warnings.add("Τα ίδια κεφάλαια ξεπερνούν το σύνολο ενεργητικού. Ελέγξτε τις τιμές.");
            if (f.containsKey(StatementField.CURRENT_ASSETS) && f.get(StatementField.CURRENT_ASSETS).value() > assets.value())
                warnings.add("Το κυκλοφορούν ενεργητικό ξεπερνά το σύνολο ενεργητικού. Ελέγξτε τις τιμές.");
            if (f.containsKey(StatementField.CURRENT_LIABILITIES) && f.get(StatementField.CURRENT_LIABILITIES).value() > assets.value())
                warnings.add("Οι βραχυπρόθεσμες υποχρεώσεις ξεπερνούν το σύνολο ενεργητικού. Ελέγξτε τις τιμές.");
        }
    }

    private static int count(String text, String needle) {
        int n = 0, i = 0;
        while ((i = text.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    // ===================== ΧΕΙΡΙΣΜΟΣ ΓΡΑΜΜΩΝ ΚΑΙ ΑΡΙΘΜΩΝ =====================

    private record Split(String label, List<String> numbers) {}

    /** Χωρίζει μια γραμμή σε ετικέτα και τους αριθμούς που βρίσκονται στο τέλος της. */
    private static Split split(String line) {
        String[] t = line.trim().split("[\\s;\\t|]+");
        int i = t.length;
        while (i > 0 && NUMBER_TOKEN.matcher(t[i - 1]).matches()) i--;
        String label = String.join(" ", Arrays.copyOfRange(t, 0, i));
        List<String> numbers = new ArrayList<>(Arrays.asList(t).subList(i, t.length));
        return new Split(label, numbers);
    }

    /** Πεζά, χωρίς τόνους, χωρίς σημεία στίξης και με ενιαία κενά. */
    static String normalize(String s) {
        return CompanyRecord.norm(s).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * Επιλέγει τη στήλη της τρέχουσας χρήσης. Προτιμώνται οι αριθμοί με μορφή ποσού (1.234,56). Η στήλη
     * «Σημείωση» (π.χ. 5) δεν μπερδεύει, γιατί δεν έχει μορφή ποσού.
     */
    private static Double pick(List<String> nums, StatementField field) {
        if (nums.isEmpty()) return null;
        String chosen = null;
        for (String n : nums) {
            if (MONEY_TOKEN.matcher(n).matches()) { chosen = n; break; }
        }
        if (chosen == null) {
            chosen = nums.size() >= 2 ? nums.get(nums.size() - 2) : nums.get(0);
        }
        Double v = parseNumber(chosen);
        if (v == null) return null;
        return ABSOLUTE.contains(field) ? Math.abs(v) : v;
    }

    /** «1.234.567,89», «(1.234,00)», «-500», «1,234,567.89» ή «-» (μηδέν). */
    static Double parseNumber(String token) {
        if (token.matches("^[-–—]$")) return 0.0;
        boolean negative = (token.startsWith("(") && token.endsWith(")"))
                || token.startsWith("-") || token.startsWith("−") || token.startsWith("–")
                || token.startsWith("(-") || token.endsWith("-");
        String d = token.replaceAll("[()\\-−–]", "");
        if (d.matches("\\d{1,3}([.,]\\d{3})+[.,]\\d{2}") && !d.matches("\\d{1,3}(\\.\\d{3})+,\\d{2}")) {
            // Ποσό όπου το OCR μπέρδεψε την τελεία με κόμμα (π.χ. 454,963,86 ή 454.963.86): τα δύο τελευταία ψηφία είναι δεκαδικά
            int lastSep = Math.max(d.lastIndexOf('.'), d.lastIndexOf(','));
            d = d.substring(0, lastSep).replaceAll("[.,]", "") + "." + d.substring(lastSep + 1);
        } else if (d.matches("\\d{1,3}(,\\d{3}){2,}(\\.\\d+)?") || d.matches("\\d{1,3}(,\\d{3})+\\.\\d+")) {
            d = d.replace(",", "");                              // αγγλική μορφή 1,234,567.89
        } else if (d.contains(",")) {
            d = d.replace(".", "").replace(',', '.');            // ελληνική μορφή 1.234.567,89
        } else if (d.matches("\\d{1,3}(\\.\\d{3})+")) {
            d = d.replace(".", "");                              // 1.234.567 (χωρίς δεκαδικά)
        }
        try {
            double v = Double.parseDouble(d);
            return negative ? -v : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
