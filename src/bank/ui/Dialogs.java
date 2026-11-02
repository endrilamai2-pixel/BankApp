package bank.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/** Κοινά παράθυρα μηνυμάτων. */
public final class Dialogs {
    private Dialogs() { }

    /** Μήνυμα επιτυχίας με κουμπί που αντιγράφει ένα κείμενο (π.χ. το IBAN) στο πρόχειρο. */
    public static void successWithCopy(Component parent, String title, String message, String copyLabel, String copyText) {
        Object[] options = {copyLabel, "ΟΚ"};
        JLabel label = new JLabel("<html><div style='width:320px'>" + message.replace("\n", "<br>") + "</div></html>");
        int choice = JOptionPane.showOptionDialog(parent, label, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText), null);
        }
    }
}
