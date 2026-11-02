package bank.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Κοινή εμφάνιση της εφαρμογής: χρώματα, στυλ κουμπιών και πινάκων, επισήμανση λαθών.
 *
 * Αν υπάρχει η βιβλιοθήκη FlatLaf στο classpath (https://www.formdev.com/flatlaf/) χρησιμοποιείται για σύγχρονη
 * εμφάνιση (στρογγυλεμένα στοιχεία, ομαλά χρώματα). Αλλιώς η εφαρμογή δουλεύει κανονικά με το look του συστήματος.
 * Η βιβλιοθήκη φορτώνεται με reflection, ώστε το project να κάνει compile και χωρίς αυτήν.
 */
public final class Theme {
    private Theme() { }

    public static final Color PRIMARY = new Color(0x1F5FBF);
    public static final Color SUCCESS = new Color(0x1E8E3E);
    public static final Color DANGER = new Color(0xC62828);
    public static final Color WARNING = new Color(0xE65100);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xD0D5DD);
    public static final Color SURFACE = new Color(0xF4F6FA);
    public static final Color ZEBRA = new Color(0xF6F8FB);

    private static boolean installed = false;
    private static boolean flat = false;

    /** Εγκαθιστά το look. Να καλείται μία φορά, πριν δημιουργηθεί οποιοδήποτε παράθυρο. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Class<?> laf = Class.forName("com.formdev.flatlaf.FlatLightLaf");
            flat = Boolean.TRUE.equals(laf.getMethod("setup").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            flat = false;
        }
        if (flat) {
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("Table.alternateRowColor", ZEBRA);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("TabbedPane.showTabSeparators", true);
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // μένει το προεπιλεγμένο look
            }
        }
        ToolTipManager.sharedInstance().setInitialDelay(300);
    }

    public static boolean isFlat() {
        return flat;
    }

    // ------------------------------------------------------------------ κείμενα

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 22f));
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN));
        l.setForeground(MUTED);
        return l;
    }

    // ------------------------------------------------------------------ κουμπιά

    /** Το κύριο κουμπί μιας οθόνης (π.χ. «Εγγραφή»): έντονο, με χρώμα. */
    public static void primary(JButton b) {
        b.setBackground(PRIMARY);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setFocusPainted(false);
        b.setMargin(new Insets(6, 18, 6, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ------------------------------------------------------------------ πάνελ

    /** Λευκή «κάρτα» με λεπτό περίγραμμα και εσωτερικό περιθώριο. */
    public static JPanel card(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(16, 20, 16, 20)));
        return p;
    }

    // ------------------------------------------------------------------ πίνακες

    /** Πιο καθαρός πίνακας: μεγαλύτερες γραμμές, εναλλασσόμενο χρώμα, έντονες επικεφαλίδες, δεξιά στοίχιση αριθμών. */
    public static void styleTable(JTable t, int... rightAlignedColumns) {
        t.setRowHeight(Math.max(t.getRowHeight(), 28));
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setFont(t.getTableHeader().getFont().deriveFont(Font.BOLD));
        t.getTableHeader().setReorderingAllowed(false);
        java.util.Set<Integer> right = new java.util.HashSet<>();
        for (int c : rightAlignedColumns) right.add(c);
        for (int i = 0; i < t.getColumnModel().getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(new ZebraRenderer(right.contains(i)));
        }
    }

    private static final class ZebraRenderer extends DefaultTableCellRenderer {
        ZebraRenderer(boolean right) {
            setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(new EmptyBorder(0, 8, 0, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            if (!selected) setBackground(row % 2 == 0 ? table.getBackground() : ZEBRA);
            return this;
        }
    }

    // ------------------------------------------------------------------ επισήμανση λαθών

    private static final String ORIGINAL_BORDER = "bank.ui.originalBorder";

    /** Κόκκινο περίγραμμα σε πεδίο με λάθος (ή αφαίρεσή του). */
    public static void markInvalid(JComponent c, boolean invalid) {
        if (flat) {
            c.putClientProperty("JComponent.outline", invalid ? "error" : null);
            return;
        }
        if (invalid) {
            if (c.getClientProperty(ORIGINAL_BORDER) == null) c.putClientProperty(ORIGINAL_BORDER, c.getBorder());
            // Ίδια συνολικά περιθώρια με το αρχικό περίγραμμα, ώστε η φόρμα να μην «πηδά» όταν εμφανιστεί το σφάλμα
            Insets in = c.getBorder() == null ? new Insets(2, 4, 2, 4) : c.getBorder().getBorderInsets(c);
            c.setBorder(new CompoundBorder(new LineBorder(DANGER, 2),
                    new EmptyBorder(Math.max(0, in.top - 2), Math.max(0, in.left - 2),
                            Math.max(0, in.bottom - 2), Math.max(0, in.right - 2))));
        } else if (c.getClientProperty(ORIGINAL_BORDER) != null) {
            c.setBorder((javax.swing.border.Border) c.getClientProperty(ORIGINAL_BORDER));
            c.putClientProperty(ORIGINAL_BORDER, null);
        }
    }
}
