package bank.auth;

import bank.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.Arrays;

/** Παράθυρο σύνδεσης. Επιτρέπει έως 3 αποτυχημένες προσπάθειες. */
public final class LoginDialog extends JDialog {

    private static final int MAX_ATTEMPTS = 3;

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel messageLabel = new JLabel(" ");
    private int attempts = 0;
    private String loggedInUser = null;

    private LoginDialog() {
        super((Frame) null, "Τράπεζα - Σύνδεση", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("Σύνδεση χρήστη", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel hint = Theme.muted("Συμπλήρωσε τα στοιχεία σου για να συνεχίσεις");
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.add(title);
        heading.add(hint);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Όνομα χρήστη:", usernameField);
        addRow(form, 1, "Κωδικός:", passwordField);
        JCheckBox showPassword = new JCheckBox("Εμφάνιση κωδικού");
        char defaultEcho = passwordField.getEchoChar();
        showPassword.addActionListener(e -> passwordField.setEchoChar(showPassword.isSelected() ? (char) 0 : defaultEcho));
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 1; sc.gridy = 2; sc.anchor = GridBagConstraints.WEST; sc.insets = new Insets(0, 5, 5, 5);
        form.add(showPassword, sc);
        // προειδοποίηση αν είναι πατημένο το Caps Lock, συχνή αιτία λάθους κωδικού
        passwordField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                try {
                    if (Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK)) {
                        messageLabel.setForeground(Theme.WARNING);
                        messageLabel.setText("Το Caps Lock είναι ενεργό.");
                    }
                } catch (UnsupportedOperationException ignored) {
                    // δεν υποστηρίζεται από το σύστημα
                }
            }
        });

        messageLabel.setForeground(new Color(0xC62828));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton loginBtn = new JButton("Σύνδεση");
        JButton exitBtn = new JButton("Έξοδος");
        Theme.primary(loginBtn);
        loginBtn.addActionListener(e -> attemptLogin());
        exitBtn.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(loginBtn);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttons.add(loginBtn);
        buttons.add(exitBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(messageLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        content.add(heading, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { usernameField.requestFocusInWindow(); }
        });
        setLocationRelativeTo(null);
    }

    /** Εμφανίζει το παράθυρο. Επιστρέφει το όνομα χρήστη ή null αν ακυρώθηκε / εξαντλήθηκαν οι προσπάθειες. */
    public static String showLogin() {
        LoginDialog d = new LoginDialog();
        d.setVisible(true);          // μπλοκάρει μέχρι να κλείσει
        return d.loggedInUser;
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        try {
            if (username.isEmpty() || password.length == 0) {
                messageLabel.setForeground(Theme.DANGER);
                messageLabel.setText("Συμπλήρωσε όνομα χρήστη και κωδικό.");
                return;
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            boolean ok;
            try {
                ok = UserDAO.authenticate(username, password);
            } catch (SQLException ex) {
                messageLabel.setForeground(Theme.DANGER);
                messageLabel.setText("Σφάλμα βάσης: " + ex.getMessage());
                return;
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }

            if (ok) {
                loggedInUser = username;
                dispose();
                return;
            }
            attempts++;
            passwordField.setText("");
            if (attempts >= MAX_ATTEMPTS) {
                JOptionPane.showMessageDialog(this, "Εξαντλήθηκαν οι προσπάθειες σύνδεσης.",
                        "Σύνδεση", JOptionPane.ERROR_MESSAGE);
                dispose();
            } else {
                messageLabel.setForeground(Theme.DANGER);
                messageLabel.setText("Λάθος στοιχεία. Απομένουν " + (MAX_ATTEMPTS - attempts) + " προσπάθειες.");
            }
        } finally {
            Arrays.fill(password, '\0');
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
