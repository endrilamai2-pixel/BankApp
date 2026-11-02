package bank.app;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Κεντρικό μενού: ο χρήστης επιλέγει ποια εφαρμογή θέλει να τρέξει. */
public final class MainMenuFrame extends JFrame {

    public MainMenuFrame(String username, Runnable openRegistration, Runnable openAccounts,
                         Runnable openLoanApp, Runnable openLedger, Runnable openCapital,
                         Runnable openCustomers, Runnable logout) {
        super("Τράπεζα - Κεντρικό Μενού");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        List<MainMenuPanel.Entry> entries = List.of(
                new MainMenuPanel.Entry("Εγγραφή νέου πελάτη", "Ιδιώτες και επιχειρήσεις, με έλεγχο ΑΑΔΕ",
                        new Color(0x1F5FBF), openRegistration),
                new MainMenuPanel.Entry("Λογαριασμοί πελατών", "Κατάθεση, ανάληψη, μεταφορά, δόσεις δανείων",
                        new Color(0x1E8E3E), openAccounts),
                new MainMenuPanel.Entry("Αξιολόγηση αίτησης δανείου", "Ιδιώτες και επιχειρήσεις",
                        new Color(0xE65100), openLoanApp),
                new MainMenuPanel.Entry("Λογιστική", "Ισολογισμός, ισοζύγιο και ημερολόγιο εγγραφών",
                        new Color(0x6A3FB5), openLedger),
                new MainMenuPanel.Entry("Διαχείριση κεφαλαίων", "Βέλτιστη κατανομή και επένδυση διαθέσιμων κεφαλαίων",
                        new Color(0x00838F), openCapital),
                new MainMenuPanel.Entry("Πελάτες", "Λίστα όλων των πελατών και κατάσταση KYC",
                        new Color(0xC2185B), openCustomers));

        setContentPane(new MainMenuPanel(username, entries, logout, () -> System.exit(0)));
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }
}
