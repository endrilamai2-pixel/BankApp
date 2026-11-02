package bank.app;

import bank.auth.FirstUserDialog;
import bank.account.AccountDAO;
import bank.account.AccountPanel;
import bank.account.LedgerPanel;
import bank.capitalInvestment.BankMain;
import bank.customers.CustomersPanel;
import bank.auth.LoginDialog;
import bank.auth.Session;
import bank.auth.UserDAO;
import bank.registration.CustomerRegistrationPanel;
import bank.registration.DatabaseManager;
import LoanApp.LoanApp.LoanGUI;

import javax.swing.*;
import java.awt.*;

/**
 * Σημείο εκκίνησης της εφαρμογής:
 * σύνδεση με τη βάση → (πρώτη φορά: δημιουργία χρήστη) → σύνδεση χρήστη → κεντρικό μενού.
 */
public final class AppLauncher {

    private static MainMenuFrame menu;
    private static JFrame registrationFrame;
    private static JFrame accountFrame;
    private static JFrame loanFrame;
    private static JFrame ledgerFrame;
    private static JFrame capitalFrame;
    private static JFrame customersFrame;

    private AppLauncher() { }

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            bank.ui.Theme.install();
            connect();
        });
    }

    /** Σύνδεση με τη βάση και προετοιμασία πινάκων, σε άλλο νήμα, με μικρό παράθυρο αναμονής. */
    private static void connect() {
        JDialog splash = new JDialog((Frame) null, "Τράπεζα", false);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        p.add(new JLabel("Σύνδεση με τη βάση δεδομένων..."), BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        splash.setContentPane(p);
        splash.pack();
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        new SwingWorker<Boolean, Void>() {
            String error;
            boolean hasUsers;

            @Override
            protected Boolean doInBackground() {
                try {
                    DatabaseManager.initialize();
                    AccountDAO.initialize();
                    UserDAO.initialize();
                    hasUsers = UserDAO.hasUsers();
                    return true;
                } catch (Exception ex) {
                    error = String.valueOf(ex.getMessage());
                    return false;
                }
            }

            @Override
            protected void done() {
                splash.dispose();
                boolean ok;
                try {
                    ok = get();
                } catch (Exception ex) {
                    ok = false;
                    error = String.valueOf(ex.getMessage());
                }
                if (!ok) {
                    int choice = JOptionPane.showOptionDialog(null,
                            "Αδυναμία σύνδεσης με τη βάση δεδομένων:\n" + error,
                            "Σφάλμα", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                            new String[]{"Επανάληψη", "Έξοδος"}, "Επανάληψη");
                    if (choice == 0) {
                        connect();
                    } else {
                        System.exit(0);
                    }
                    return;
                }
                if (!hasUsers && !FirstUserDialog.showSetup()) {
                    System.exit(0);       // δεν δημιουργήθηκε χρήστης: δεν υπάρχει πρόσβαση
                }
                login();
            }
        }.execute();
    }

    private static void login() {
        String user = LoginDialog.showLogin();
        if (user == null) {
            System.exit(0);
        }
        Session.setUser(user);
        menu = new MainMenuFrame(user,
                AppLauncher::openRegistration,
                AppLauncher::openAccounts,
                AppLauncher::openLoanApp,
                AppLauncher::openLedger,
                AppLauncher::openCapital,
                AppLauncher::openCustomers,
                AppLauncher::logout);
        menu.setVisible(true);
    }

    private static void logout() {
        for (Window w : new Window[]{registrationFrame, accountFrame, loanFrame, ledgerFrame, capitalFrame, customersFrame, menu}) {
            if (w != null) w.dispose();
        }
        registrationFrame = null;
        accountFrame = null;
        loanFrame = null;
        ledgerFrame = null;
        capitalFrame = null;
        customersFrame = null;
        menu = null;
        Session.setUser(null);
        login();
    }

    /** Όταν κλείσει ένα παράθυρο εφαρμογής (κουμπί «Πίσω» ή X), το κεντρικό μενού έρχεται μπροστά. */
    private static void returnToMenuOnClose(JFrame frame) {
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                if (menu != null && menu.isDisplayable()) menu.toFront();
            }
        });
    }

    private static void openCustomers() {
        if (customersFrame != null && customersFrame.isDisplayable()) {
            customersFrame.toFront();
            return;
        }
        CustomersPanel panel = new CustomersPanel();
        customersFrame = new JFrame("Τράπεζα - Πελάτες");
        customersFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        customersFrame.setContentPane(bank.ui.Navigation.withBackBar(customersFrame, panel));
        returnToMenuOnClose(customersFrame);
        // Η λίστα ανανεώνεται κάθε φορά που το παράθυρο ξαναγίνεται ενεργό (π.χ. μετά από νέα εγγραφή πελάτη)
        customersFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) { panel.reload(); }
        });
        customersFrame.pack();
        customersFrame.setLocationRelativeTo(null);
        customersFrame.setVisible(true);
    }

    private static void openRegistration() {
        if (registrationFrame != null && registrationFrame.isDisplayable()) {
            registrationFrame.toFront();
            return;
        }
        registrationFrame = new JFrame("Τράπεζα - Εγγραφή Πελάτη");
        registrationFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        registrationFrame.setContentPane(bank.ui.Navigation.withBackBar(registrationFrame, new CustomerRegistrationPanel()));
        returnToMenuOnClose(registrationFrame);
        registrationFrame.pack();
        registrationFrame.setLocationRelativeTo(null);
        registrationFrame.setVisible(true);
    }

    private static void openAccounts() {
        if (accountFrame != null && accountFrame.isDisplayable()) {
            accountFrame.toFront();
            return;
        }
        accountFrame = new JFrame("Τράπεζα - Λογαριασμοί πελατών");
        accountFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        accountFrame.setContentPane(bank.ui.Navigation.withBackBar(accountFrame, new AccountPanel()));
        returnToMenuOnClose(accountFrame);
        accountFrame.pack();
        accountFrame.setLocationRelativeTo(null);
        accountFrame.setVisible(true);
    }

    private static void openCapital() {
        if (capitalFrame != null && capitalFrame.isDisplayable()) {
            capitalFrame.toFront();
            return;
        }
        capitalFrame = BankMain.openFrame();
        returnToMenuOnClose(capitalFrame);
    }

    private static void openLedger() {
        if (ledgerFrame != null && ledgerFrame.isDisplayable()) {
            ledgerFrame.toFront();
            return;
        }
        ledgerFrame = new JFrame("Τράπεζα - Λογιστική");
        ledgerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        ledgerFrame.setContentPane(bank.ui.Navigation.withBackBar(ledgerFrame, new LedgerPanel()));
        returnToMenuOnClose(ledgerFrame);
        ledgerFrame.pack();
        ledgerFrame.setLocationRelativeTo(null);
        ledgerFrame.setVisible(true);
    }

    private static void openLoanApp() {
        if (loanFrame != null && loanFrame.isDisplayable()) {
            loanFrame.toFront();
            return;
        }
        loanFrame = LoanGUI.createFrame();
        returnToMenuOnClose(loanFrame);
        loanFrame.setVisible(true);
    }
}
