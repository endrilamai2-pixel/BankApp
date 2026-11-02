package bank.account;

import bank.registration.IbanGenerator;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Locale;

/** Παράθυρα επιβεβαίωσης και ολοκλήρωσης της εκταμίευσης. */
public final class DisbursementDialog {

    private static final Locale GR = Locale.forLanguageTag("el-GR");

    private DisbursementDialog() { }

    /** Ζητά επιβεβαίωση πριν γίνει η πίστωση. Επιστρέφει true αν ο χρήστης πάτησε «Πίστωση». */
    public static boolean confirm(Component parent, String holderName, String iban, double amount) {
        String ibanText = (iban == null || iban.isBlank())
                ? "<i>θα δημιουργηθεί αυτόματα</i>" : IbanGenerator.format(iban.trim());
        String html = "<html><table cellpadding='3'>"
                + row("Δικαιούχος", esc(holderName))
                + row("IBAN", ibanText)
                + row("Ποσό δανείου", "<b>" + money(amount) + "</b>")
                + "</table><br>Να πιστωθεί το ποσό στον λογαριασμό του πελάτη;</html>";
        Object[] options = {"Πίστωση", "Ακύρωση"};
        int choice = JOptionPane.showOptionDialog(parent, new JLabel(html), "Εκταμίευση δανείου",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    public static void showSuccess(Component parent, String holderName, Disbursement d, double amount) {
        String html = "<html><table cellpadding='3'>"
                + row("Αριθμός δανείου", "#" + d.loanId())
                + row("Δικαιούχος", esc(holderName))
                + row("IBAN", IbanGenerator.format(d.iban()))
                + row("Πιστώθηκε", "<b>" + money(amount) + "</b>")
                + row("Νέο υπόλοιπο", "<b>" + String.format(GR, "%,.2f €", d.newBalance()) + "</b>")
                + "</table></html>";
        JOptionPane.showMessageDialog(parent, new JLabel(html), "Το δάνειο πιστώθηκε",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String row(String label, String value) {
        return "<tr><td><font color='gray'>" + label + "</font></td><td>" + value + "</td></tr>";
    }

    private static String money(double v) {
        return String.format(GR, "%,.2f €", v);
    }

    private static String esc(String s) {
        return s == null ? "—" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
