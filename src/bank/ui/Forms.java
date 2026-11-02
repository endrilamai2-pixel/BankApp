package bank.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import java.awt.event.HierarchyEvent;
import java.util.List;

/** Μικρά βοηθητικά για φόρμες: πεδία ψηφίων, αντίδραση σε αλλαγές, μετάβαση με Enter, αρχική εστίαση. */
public final class Forms {
    private Forms() { }

    /** Δέχεται μόνο ψηφία (και στο επικόλληση) και μέχρι {@code maxLength} χαρακτήρες. */
    public static void digitsOnly(JTextField field, int maxLength) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
                    throws BadLocationException {
                String digits = text == null ? "" : text.replaceAll("\\D", "");
                int room = maxLength - (fb.getDocument().getLength() - length);
                if (!digits.isEmpty() && room <= 0) return;
                if (digits.length() > room) digits = digits.substring(0, room);
                super.replace(fb, offset, length, digits, attr);
            }
        });
    }

    /**
     * Πεδίο ημερομηνίας ΗΗ/ΜΜ/ΕΕΕΕ: δέχεται μόνο ψηφία (μέχρι 8) και βάζει μόνο του τις καθέτους.
     * Δουλεύει και με επικόλληση (π.χ. «12051990» ή «12/05/1990») και με σβήσιμο (το Backspace πάνω από
     * κάθετο σβήνει και το ψηφίο πριν από αυτήν).
     */
    public static void dateField(JTextField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                replace(fb, offset, length, "", null);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String inserted = text == null ? "" : text.replaceAll("\\D", "");
                if (text != null && !text.isEmpty() && inserted.isEmpty()) return;   // γράμματα/σύμβολα: αγνοούνται

                int start = offset, end = offset + length;
                // Backspace πάνω από κάθετο: σβήνεται και το ψηφίο πριν από αυτήν
                if (text != null && text.isEmpty() && length == 1 && current.charAt(offset) == '/' && start > 0) {
                    start--;
                }
                String before = current.substring(0, start).replaceAll("\\D", "");
                String after = current.substring(end).replaceAll("\\D", "");
                String digits = before + inserted + after;
                if (digits.length() > 8) {
                    digits = digits.substring(0, 8);
                    if (before.length() + inserted.length() > 8) inserted = inserted.substring(0, 8 - before.length());
                }
                int caretDigits = Math.min(before.length() + inserted.length(), digits.length());
                boolean typedAtEnd = after.isEmpty() && !inserted.isEmpty();

                String formatted = formatDate(digits, typedAtEnd);
                fb.replace(0, fb.getDocument().getLength(), formatted, attr);

                int caret = caretDigits + (caretDigits > 2 ? 1 : 0) + (caretDigits > 4 ? 1 : 0);
                // Αν μόλις γράφτηκε το 2ο ή 4ο ψηφίο στο τέλος, ο κέρσορας πάει μετά την κάθετο
                if (typedAtEnd && (caretDigits == 2 || caretDigits == 4)) caret++;
                int pos = Math.min(caret, formatted.length());
                SwingUtilities.invokeLater(() -> {
                    if (pos <= field.getDocument().getLength()) field.setCaretPosition(pos);
                });
            }
        });
    }

    private static String formatDate(String digits, boolean trailingSlash) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i == 2 || i == 4) sb.append('/');
            sb.append(digits.charAt(i));
        }
        if (trailingSlash && (digits.length() == 2 || digits.length() == 4)) sb.append('/');
        return sb.toString();
    }

    /** Εκτελεί την ενέργεια σε κάθε αλλαγή του κειμένου. */
    public static void onChange(JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); }
            public void removeUpdate(DocumentEvent e) { action.run(); }
            public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    /** Το Enter μεταφέρει στο επόμενο πεδίο· στο τελευταίο εκτελεί την ενέργεια (π.χ. «Εγγραφή»). */
    public static void enterMovesFocus(List<? extends JTextField> fields, Runnable onLast) {
        for (int i = 0; i < fields.size(); i++) {
            JTextField next = i + 1 < fields.size() ? fields.get(i + 1) : null;
            fields.get(i).addActionListener(e -> {
                if (next != null) next.requestFocusInWindow(); else onLast.run();
            });
        }
    }

    /** Δίνει εστίαση στο πεδίο την πρώτη φορά που θα εμφανιστεί το πάνελ. */
    public static void focusWhenShown(JComponent panel, JComponent target) {
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) {
                SwingUtilities.invokeLater(target::requestFocusInWindow);
            }
        });
    }
}
