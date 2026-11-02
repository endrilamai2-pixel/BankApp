package bank.registration;

import javax.swing.*;

/** Δοκιμαστική εκκίνηση του πάνελ μέσα σε παράθυρο. */
public class Main {
    public static void main(String[] args) {
        try {
            DatabaseManager.initialize();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Αδυναμία σύνδεσης με τη βάση:\n" + e.getMessage(),
                "Σφάλμα", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Τράπεζα - Εγγραφή Πελάτη");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new CustomerRegistrationPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
