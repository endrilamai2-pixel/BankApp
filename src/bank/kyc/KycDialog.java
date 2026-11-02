package bank.kyc;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import java.awt.Component;

/** Popup με το αποτέλεσμα του ελέγχου ΑΑΔΕ. */
public final class KycDialog {

    private KycDialog() { }

    public static String html(KycChecker.Outcome o) {
        String color = switch (o.status()) {
            case VERIFIED -> "#1a7f37";
            case WARNING, UNAVAILABLE -> "#b26a00";
            case REJECTED -> "#c62828";
        };
        StringBuilder sb = new StringBuilder("<html><body style='width:380px'>");
        sb.append("<p><b><font color='").append(color).append("'>Έλεγχος ΑΑΔΕ: ")
          .append(o.status().label).append("</font></b></p>");
        for (String m : o.messages()) {
            sb.append("<p>• ").append(esc(m)).append("</p>");
        }
        AadeRecord r = o.record();
        if (r != null) {
            sb.append("<table cellpadding='2'>");
            row(sb, "ΑΦΜ", r.afm());
            row(sb, "Επωνυμία", r.name());
            row(sb, "Διακριτικός τίτλος", r.tradeTitle());
            row(sb, "Τύπος", r.naturalPerson() ? "Φυσικό πρόσωπο" : r.legalStatus());
            row(sb, "Κατάσταση ΑΦΜ", r.deactivationDescr());
            row(sb, "ΔΟΥ", r.taxOffice());
            row(sb, "Διεύθυνση", r.address());
            row(sb, "Έναρξη", r.registrationDate() == null ? "" : r.registrationDate().toString());
            row(sb, "Παύση", r.stopDate() == null ? "" : r.stopDate().toString());
            AadeRecord.Activity a = r.primaryActivity();
            row(sb, "Κύρια δραστηριότητα", a == null ? "" : a.code() + " " + a.description());
            sb.append("</table>");
        }
        sb.append("<p><font color='gray'>Πηγή: ").append(esc(o.source())).append("</font></p></body></html>");
        return sb.toString();
    }

    public static void show(Component parent, KycChecker.Outcome o) {
        int type = o.status() == KycChecker.Status.REJECTED ? JOptionPane.ERROR_MESSAGE
                : o.status() == KycChecker.Status.VERIFIED ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(parent, new JLabel(html(o)), "Έλεγχος ΑΑΔΕ (KYC)", type);
    }

    private static void row(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append("<tr><td><font color='gray'>").append(label).append("</font></td><td><b>")
          .append(esc(value)).append("</b></td></tr>");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
