package bank.ui;

import javax.swing.*;
import java.awt.*;

/** Κοινή πλοήγηση: γραμμή με κουμπί «Πίσω» στο πάνω μέρος κάθε παραθύρου εφαρμογής. */
public final class Navigation {
    private Navigation() { }

    public static final String BACK_TEXT = "← Πίσω στο κεντρικό μενού";

    /**
     * Τυλίγει το περιεχόμενο ενός παραθύρου με γραμμή που έχει κουμπί «Πίσω». Το κουμπί κλείνει το παράθυρο,
     * οπότε ο χρήστης επιστρέφει στο κεντρικό μενού (που παραμένει ανοιχτό).
     */
    public static JPanel withBackBar(Window window, Component content) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(backBar(window), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    public static JComponent backBar(Window window) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        JButton back = new JButton(BACK_TEXT);
        back.setFocusPainted(false);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> window.dispose());
        bar.add(back);
        return bar;
    }
}
