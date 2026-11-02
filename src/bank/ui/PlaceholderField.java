package bank.ui;

import javax.swing.*;
import java.awt.*;

/** Πεδίο κειμένου που δείχνει γκρι υπόδειξη («π.χ. 123456789») όσο είναι άδειο. */
public class PlaceholderField extends JTextField {

    private String placeholder = "";

    public PlaceholderField(int columns) {
        super(columns);
    }

    public PlaceholderField(int columns, String placeholder) {
        super(columns);
        this.placeholder = placeholder;
    }

    public PlaceholderField placeholder(String text) {
        this.placeholder = text;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && !placeholder.isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.MUTED);
            g2.setFont(getFont());
            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            int y = in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, in.left + 2, y);
            g2.dispose();
        }
    }
}
