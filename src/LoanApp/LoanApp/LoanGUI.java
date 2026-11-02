package LoanApp.LoanApp;

import javax.swing.*;

/** Παράθυρο αξιολόγησης δανείων, με καρτέλες για ιδιώτες και επιχειρήσεις. */
public class LoanGUI {

    /** Δημιουργεί το παράθυρο (δεν το εμφανίζει). Να καλείται από το νήμα των Swing. */
    public static JFrame createFrame() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ιδιώτης", new LoanPanel());
        tabs.addTab("Επιχείρηση", new BusinessLoanPanel());
        tabs.setFont(tabs.getFont().deriveFont(java.awt.Font.BOLD, 14f));

        JFrame frame = new JFrame("Αξιολόγηση Αίτησης Δανείου");
        // Κλείνει μόνο αυτό το παράθυρο· το κεντρικό μενού παραμένει ανοιχτό
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(bank.ui.Navigation.withBackBar(frame, tabs));
        frame.pack();
        // Το παράθυρο δεν πρέπει να ξεπερνά την οθόνη (χωρίς taskbar)
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(frame.getWidth(), screen.width), Math.min(frame.getHeight(), screen.height));
        frame.setLocationRelativeTo(null);   // στο κέντρο της οθόνης
        return frame;
    }

    /** Ανοίγει απευθείας το παράθυρο, χωρίς κεντρικό μενού και χωρίς σύνδεση χρήστη (μόνο για δοκιμές). */
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            bank.ui.Theme.install();
            JFrame frame = createFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
