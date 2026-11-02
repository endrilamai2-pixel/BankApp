package bank.auth;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;

/** Εμφανίζεται μόνο στην πρώτη εκτέλεση (όταν δεν υπάρχει κανένας χρήστης) για τη δημιουργία του πρώτου λογαριασμού. */
public final class FirstUserDialog extends JDialog {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);
    private final JLabel messageLabel = new JLabel(" ");
    private boolean created = false;

    private FirstUserDialog() {
        super((Frame) null, "Τράπεζα - Πρώτη εκτέλεση", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("<html><div style='text-align:center'><b>Δημιουργία πρώτου χρήστη</b><br>"
                + "<font size='-1'>Δεν υπάρχει ακόμα κανένας χρήστης. Δημιούργησε τον λογαριασμό διαχειριστή.</font></div></html>",
                SwingConstants.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Όνομα χρήστη:", usernameField);
        addRow(form, 1, "Κωδικός:", passwordField);
        addRow(form, 2, "Επιβεβαίωση κωδικού:", confirmField);

        messageLabel.setForeground(new Color(0xC62828));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton createBtn = new JButton("Δημιουργία");
        JButton cancelBtn = new JButton("Ακύρωση");
        createBtn.addActionListener(e -> create());
        cancelBtn.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(createBtn);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttons.add(createBtn);
        buttons.add(cancelBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(messageLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        content.add(title, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    /** Επιστρέφει true αν δημιουργήθηκε ο χρήστης. */
    public static boolean showSetup() {
        FirstUserDialog d = new FirstUserDialog();
        d.setVisible(true);
        return d.created;
    }

    private void create() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();
        try {
            if (username.length() < 3 || username.length() > 50) {
                messageLabel.setText("Το όνομα χρήστη πρέπει να έχει 3 έως 50 χαρακτήρες.");
            } else if (password.length < MIN_PASSWORD_LENGTH) {
                messageLabel.setText("Ο κωδικός πρέπει να έχει τουλάχιστον " + MIN_PASSWORD_LENGTH + " χαρακτήρες.");
            } else if (!Arrays.equals(password, confirm)) {
                messageLabel.setText("Οι δύο κωδικοί δεν ταιριάζουν.");
            } else {
                try {
                    UserDAO.createUser(username, password);
                    created = true;
                    dispose();
                } catch (SQLException ex) {
                    messageLabel.setText("Σφάλμα βάσης: " + ex.getMessage());
                }
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private static void addRow(JPanel p, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }
}
