package bank.registration;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import java.awt.Component;

/** Popup με τα στοιχεία ενός πελάτη που υπάρχει ήδη στο σύστημα. */
public final class CustomerInfoDialog {

    private CustomerInfoDialog() { }

    public static void showIndividual(Component parent, IndividualCustomer c) {
        showIndividual(parent, c, "Ο πελάτης υπάρχει ήδη στο σύστημα");
    }

    public static void showIndividual(Component parent, IndividualCustomer c, String title) {
        StringBuilder html = new StringBuilder("<html><table cellpadding='3'>");
        row(html, "Όνομα", c.firstName());
        row(html, "Επώνυμο", c.lastName());
        row(html, "Αριθμός Ταυτότητας", c.idNumber());
        row(html, "ΑΦΜ", c.afm());
        row(html, "Επάγγελμα", c.profession());
        row(html, "Ημερομηνία Γέννησης", c.formattedDateOfBirth());
        row(html, "Τηλέφωνο", c.phone());
        row(html, "Διεύθυνση Κατοικίας", c.homeAddress());
        row(html, "IBAN", formatIban(c.iban()));
        row(html, "Αρ. λογαριασμού", IbanGenerator.accountNumber(c.iban()));
        row(html, "Υπόλοιπο λογαριασμού", formatBalance(c.balance()));
        html.append("</table></html>");
        show(parent, title, html);
    }

    public static void showBusiness(Component parent, BusinessCustomer c) {
        showBusiness(parent, c, "Η επιχείρηση υπάρχει ήδη στο σύστημα");
    }

    public static void showBusiness(Component parent, BusinessCustomer c, String title) {
        StringBuilder html = new StringBuilder("<html><table cellpadding='3'>");
        row(html, "Επωνυμία", c.companyName());
        row(html, "Έδρα", c.headquarters());
        row(html, "ΑΦΜ", c.afm());
        row(html, "Δραστηριότητα", c.activity());
        row(html, "IBAN", formatIban(c.iban()));
        row(html, "Αρ. λογαριασμού", IbanGenerator.accountNumber(c.iban()));
        row(html, "Υπόλοιπο λογαριασμού", formatBalance(c.balance()));
        html.append("</table></html>");
        show(parent, title, html);
    }

    private static void show(Component parent, String title, StringBuilder html) {
        JOptionPane.showMessageDialog(parent, new JLabel(html.toString()), title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td><font color='gray'>").append(label).append("</font></td><td><b>")
          .append(esc(value)).append("</b></td></tr>");
    }

    private static String formatIban(String iban) {
        return (iban == null || iban.isBlank()) ? "—" : IbanGenerator.format(iban.trim());
    }

    private static String formatBalance(java.math.BigDecimal b) {
        return String.format(java.util.Locale.forLanguageTag("el-GR"), "%,.2f €",
                b != null ? b : java.math.BigDecimal.ZERO);
    }

    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
