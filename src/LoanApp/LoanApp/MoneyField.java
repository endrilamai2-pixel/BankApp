package LoanApp.LoanApp;

import javax.swing.*;
import javax.swing.text.*;

/**
 * Πεδίο ποσού που διαχωρίζει αυτόματα τις χιλιάδες με τελεία καθώς πληκτρολογείς: 1250000 -> 1.250.000.
 * Δεκαδικά με κόμμα (έως 2 ψηφία). Οι τελείες που πληκτρολογεί ο χρήστης αγνοούνται και ξαναβάζονται σωστά,
 * ενώ γράμματα και άλλοι χαρακτήρες δεν γίνονται δεκτοί.
 */
public class MoneyField extends JTextField {

    private final boolean allowNegative;

    /** @param allowNegative αν επιτρέπεται αρνητικό πρόσημο (π.χ. ίδια κεφάλαια, ζημίες) */
    public MoneyField(String initial, boolean allowNegative) {
        super(10);
        this.allowNegative = allowNegative;
        ((AbstractDocument) getDocument()).setDocumentFilter(new Filter());
        setText(initial);
    }

    /** Το ποσό ως αριθμός (τελείες = χιλιάδες, κόμμα = δεκαδικά). NaN αν το πεδίο δεν είναι έγκυρο. */
    public double getAmount() {
        try {
            return Double.parseDouble(toPlain(getText()));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** "1.250.000,50" -> "1250000.50" */
    public static String toPlain(String formatted) {
        return formatted.trim().replace(".", "").replace(',', '.');
    }

    /** Επαναδιατύπωση του κειμένου: κρατά ψηφία, ένα κόμμα και (προαιρετικά) αρχικό «-», και βάζει τελείες. */
    static String format(String raw, boolean allowNegative) {
        StringBuilder digits = new StringBuilder();
        StringBuilder frac = new StringBuilder();
        boolean negative = false;
        boolean hasComma = false;

        for (char c : raw.toCharArray()) {
            if (c == '-' && allowNegative && !negative && digits.length() == 0 && !hasComma) {
                negative = true;
            } else if (Character.isDigit(c)) {
                if (hasComma) {
                    if (frac.length() < 2) frac.append(c);
                } else {
                    digits.append(c);
                }
            } else if (c == ',' && !hasComma) {
                hasComma = true;
            }
            // οτιδήποτε άλλο (τελείες, κενά, γράμματα) αγνοείται
        }

        // Αφαίρεση αρχικών μηδενικών: 007 -> 7
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') start++;
        String integer = digits.substring(start);

        // Τελείες κάθε 3 ψηφία από το τέλος
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) grouped.append('.');
            grouped.append(integer.charAt(i));
        }

        return (negative ? "-" : "") + grouped + (hasComma ? "," + frac : "");
    }

    private static boolean isSignificant(char c) {
        return Character.isDigit(c) || c == ',' || c == '-';
    }

    /** Φιλτράρει κάθε αλλαγή του κειμένου, ώστε να εμφανίζεται πάντα μορφοποιημένο και να μένει σωστός ο κέρσορας. */
    private class Filter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String str, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, str, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String str, AttributeSet attr)
                throws BadLocationException {
            Document doc = fb.getDocument();
            String inserted = str == null ? "" : str;
            String current = doc.getText(0, doc.getLength());
            String raw = new StringBuilder(current).replace(offset, offset + length, inserted).toString();
            String formatted = format(raw, allowNegative);

            if (formatted.equals(raw)) {                 // δεν χρειάζεται αλλαγή
                super.replace(fb, offset, length, inserted, attr);
                return;
            }

            // Πόσοι «σημαντικοί» χαρακτήρες (ψηφία, κόμμα, πρόσημο) υπάρχουν πριν τον κέρσορα
            int caretInRaw = offset + inserted.length();
            int significant = 0;
            for (int i = 0; i < caretInRaw && i < raw.length(); i++) {
                if (isSignificant(raw.charAt(i))) significant++;
            }

            fb.replace(0, doc.getLength(), formatted, attr);

            // Ο κέρσορας μπαίνει μετά τον ίδιο αριθμό σημαντικών χαρακτήρων στο νέο κείμενο
            int pos = 0, seen = 0;
            while (pos < formatted.length() && seen < significant) {
                if (isSignificant(formatted.charAt(pos))) seen++;
                pos++;
            }
            setCaretPosition(Math.min(pos, formatted.length()));
        }
    }
}
