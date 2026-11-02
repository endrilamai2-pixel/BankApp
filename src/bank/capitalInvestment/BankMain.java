package bank.capitalInvestment;


    import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

    /** Ανοίγει την εφαρμογή διαχείρισης κεφαλαίου τράπεζας. */
    public class BankMain {

        public static void launch() {
            SwingUtilities.invokeLater(() -> {
                bank.ui.Theme.install();
                BankPanel panel = new BankPanel();
                JFrame frame = new JFrame("Διαχείριση κεφαλαίων");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setContentPane(panel);
                frame.pack();
                // Το παράθυρο δεν πρέπει να ξεπερνά την οθόνη (χωρίς taskbar)
                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                frame.setSize(Math.min(frame.getWidth(), screen.width), Math.min(frame.getHeight(), screen.height));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.calculate();   // αρχικός υπολογισμός με τις προεπιλεγμένες παραδοχές
            });
        }

        /**
         * Ανοίγει την οθόνη μέσα από την εφαρμογή της τράπεζας (πρέπει να κληθεί στο νήμα του Swing): το κλείσιμο δεν
         * τερματίζει το πρόγραμμα και τα κεφάλαια φορτώνονται από το καθολικό αν υπάρχουν δεδομένα εκεί.
         */
        public static JFrame openFrame() {
            BankPanel panel = new BankPanel();
            JFrame frame = new JFrame("Τράπεζα - Διαχείριση κεφαλαίων");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(bank.ui.Navigation.withBackBar(frame, panel));
            frame.pack();
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setSize(Math.min(frame.getWidth(), screen.width), Math.min(frame.getHeight(), screen.height));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.loadFromLedger(true, panel::calculate);
            return frame;
        }

        public static void main(String[] args) {
            launch();
        }
    }


