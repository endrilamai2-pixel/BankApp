package bank.app;

import bank.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Το περιεχόμενο του κεντρικού μενού: κάρτες επιλογής με χρώμα, συντομεύσεις 1-6 και πλοήγηση από το πληκτρολόγιο. */
public final class MainMenuPanel extends JPanel {

    /** Μία επιλογή του μενού. */
    public record Entry(String title, String subtitle, Color accent, Runnable action) { }

    public MainMenuPanel(String username, List<Entry> entries, Runnable logout, Runnable exit) {
        super(new BorderLayout());

        add(header(username, logout), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setBackground(Theme.SURFACE);
        grid.setBorder(BorderFactory.createEmptyBorder(24, 24, 12, 24));
        int n = 1;
        for (Entry e : entries) {
            Card card = new Card(n, e);
            grid.add(card);
            bindShortcut(n, card);
            n++;
        }
        if (entries.size() % 2 == 1) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }
        add(grid, BorderLayout.CENTER);
        add(footer(entries.size(), exit), BorderLayout.SOUTH);
    }

    private JPanel header(String username, Runnable logout) {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(Theme.PRIMARY);
        h.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        JLabel name = new JLabel("Τράπεζα");
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 24f));
        JLabel sub = new JLabel("Κεντρικό μενού – επιλέξτε εφαρμογή");
        sub.setForeground(new Color(0xD6E4FF));
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(name);
        left.add(sub);

        JLabel user = new JLabel(username);
        user.setForeground(Color.WHITE);
        user.setFont(user.getFont().deriveFont(Font.BOLD));
        JButton logoutBtn = new JButton("Αποσύνδεση");
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> logout.run());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        right.setOpaque(false);
        right.add(user);
        right.add(logoutBtn);

        h.add(left, BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JPanel footer(int count, Runnable exit) {
        JPanel f = new JPanel(new BorderLayout());
        f.setBackground(Theme.SURFACE);
        f.setBorder(BorderFactory.createEmptyBorder(4, 24, 14, 24));
        f.add(Theme.muted("Συντομεύσεις: πλήκτρα 1–" + count + " ή Tab και Enter"), BorderLayout.WEST);
        JButton exitBtn = new JButton("Έξοδος");
        exitBtn.addActionListener(e -> exit.run());
        f.add(exitBtn, BorderLayout.EAST);
        return f;
    }

    private void bindShortcut(int n, Card card) {
        String key = "open-" + n;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(String.valueOf(n)), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { card.activate(); }
        });
    }

    /** Κάρτα επιλογής: ζωγραφίζεται με στρογγυλεμένες γωνίες, αλλάζει χρώμα στο hover και έχει δείκτη εστίασης. */
    private static final class Card extends JPanel {
        private final Entry entry;
        private final int number;
        private boolean hover;

        Card(int number, Entry entry) {
            super(new BorderLayout(0, 4));
            this.entry = entry;
            this.number = number;
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(18, 26, 18, 18));
            setPreferredSize(new Dimension(290, 130));
            getAccessibleContext().setAccessibleName(entry.title());

            // Ο τίτλος αφήνει χώρο δεξιά για τον αριθμό συντόμευσης και αλλάζει γραμμή αν είναι μεγάλος
            JLabel title = new JLabel("<html><div style='width:175px'><b>" + entry.title() + "</b></div></html>");
            title.setFont(title.getFont().deriveFont(Font.PLAIN, 16f));
            title.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 34));
            JLabel subtitle = new JLabel("<html><div style='width:200px'>" + entry.subtitle() + "</div></html>");
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13f));
            subtitle.setForeground(Theme.MUTED);
            subtitle.setVerticalAlignment(SwingConstants.TOP);
            add(title, BorderLayout.NORTH);
            add(subtitle, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); activate(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) activate();
                }
            });
        }

        void activate() {
            entry.action().run();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 1, h = getHeight() - 1;
            Shape shape = new RoundRectangle2D.Float(0, 0, w, h, 16, 16);

            g2.setColor(hover ? new Color(0xEEF4FF) : Color.WHITE);
            g2.fill(shape);

            // χρωματιστή λωρίδα στα αριστερά
            Shape oldClip = g2.getClip();
            g2.clip(shape);
            g2.setColor(entry.accent());
            g2.fillRect(0, 0, 8, getHeight());
            g2.setClip(oldClip);

            g2.setColor(isFocusOwner() ? Theme.PRIMARY : Theme.BORDER);
            g2.setStroke(new BasicStroke(isFocusOwner() ? 2.5f : 1f));
            g2.draw(shape);

            // αριθμός συντόμευσης
            int d = 24, x = w - d - 12, y = 12;
            g2.setColor(new Color(entry.accent().getRed(), entry.accent().getGreen(), entry.accent().getBlue(), 28));
            g2.fillOval(x, y, d, d);
            g2.setColor(entry.accent());
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            String s = String.valueOf(number);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, x + (d - fm.stringWidth(s)) / 2, y + (d - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
