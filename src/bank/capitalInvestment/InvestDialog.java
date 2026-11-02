package bank.capitalInvestment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Παράθυρο επένδυσης: δείχνει τα διαθέσιμα μετρητά και τη σημερινή θέση, προτείνει αγορές με βάση τη βέλτιστη κατανομή
 * του μοντέλου και ελέγχει σε πραγματικό χρόνο ποιοι ρυθμιστικοί περιορισμοί θα παραβιάζονταν.
 */
final class InvestDialog extends JDialog {

    private static final Locale GR = Locale.forLanguageTag("el-GR");

    private final LedgerLink.Position pos;
    private final Assumptions base;
    private final Map<AssetClass, AmountField> inputs = new EnumMap<>(AssetClass.class);
    private final JLabel warnings = new JLabel(" ");
    private final Consumer<Map<AssetClass, BigDecimal>> onConfirm;

    InvestDialog(Window owner, LedgerLink.Position pos, Assumptions base, Map<AssetClass, Double> suggestion,
                 Consumer<Map<AssetClass, BigDecimal>> onConfirm) {
        super(owner, "Επένδυση κεφαλαίων", ModalityType.APPLICATION_MODAL);
        this.pos = pos;
        this.base = base;
        this.onConfirm = onConfirm;

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel info = new JLabel(String.format(GR,
                "<html><b>Διαθέσιμα μετρητά στο ταμείο:</b> %s<br>Συνολικό ενεργητικό: %s · Ίδια κεφάλαια: %s · Καταθέσεις: %s</html>",
                Analysis.euro(pos.spendableCash()), Analysis.euro(pos.totalAssets()),
                Analysis.euro(pos.ownFunds()), Analysis.euro(pos.deposits())));
        content.add(info, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        String[] heads = {"Κατηγορία", "Σημερινή θέση", "Πρόταση μοντέλου", "Ποσό επένδυσης (€)"};
        for (int i = 0; i < heads.length; i++) {
            c.gridx = i; c.gridy = 0;
            JLabel h = new JLabel(heads[i]);
            h.setFont(h.getFont().deriveFont(Font.BOLD));
            grid.add(h, c);
        }
        int row = 1;
        DocumentListener recheck = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { recheck(); }
            public void removeUpdate(DocumentEvent e) { recheck(); }
            public void changedUpdate(DocumentEvent e) { recheck(); }
        };
        for (AssetClass cls : LedgerLink.INVESTABLE.keySet()) {
            double suggested = suggestion.getOrDefault(cls, 0.0);
            AmountField f = new AmountField(String.valueOf((long) suggested), false);
            f.getDocument().addDocumentListener(recheck);
            inputs.put(cls, f);
            c.gridy = row;
            c.gridx = 0; grid.add(new JLabel(cls.label), c);
            c.gridx = 1; grid.add(new JLabel(Analysis.euro(pos.amounts()[cls.ordinal()])), c);
            c.gridx = 2; grid.add(new JLabel(suggestion.isEmpty() ? "—" : Analysis.euro(suggested)), c);
            c.gridx = 3; grid.add(f, c);
            row++;
        }
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(grid, BorderLayout.NORTH);
        warnings.setVerticalAlignment(SwingConstants.TOP);
        JScrollPane scroll = new JScrollPane(warnings);
        scroll.setPreferredSize(new Dimension(620, 130));
        center.add(scroll, BorderLayout.CENTER);
        if (suggestion.isEmpty()) {
            JLabel hint = new JLabel("Για να δείτε προτάσεις, πατήστε πρώτα «Υπολογισμός βέλτιστης κίνησης».");
            hint.setForeground(Color.GRAY);
            center.add(hint, BorderLayout.SOUTH);
        }
        content.add(center, BorderLayout.CENTER);

        JButton ok = new JButton("Επένδυση");
        JButton cancel = new JButton("Ακύρωση");
        ok.addActionListener(e -> confirm());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        recheck();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Τα ποσά που έχουν πληκτρολογηθεί. Ρίχνει IllegalArgumentException αν κάποιο δεν είναι έγκυρο. */
    private Map<AssetClass, Double> entered() {
        Map<AssetClass, Double> m = new EnumMap<>(AssetClass.class);
        for (Map.Entry<AssetClass, AmountField> e : inputs.entrySet()) {
            double v = e.getValue().getAmount();
            if (Double.isNaN(v) || v < 0) throw new IllegalArgumentException("Μη έγκυρο ποσό στο «" + e.getKey().label + "».");
            m.put(e.getKey(), v);
        }
        return m;
    }

    private void recheck() {
        try {
            Map<AssetClass, Double> m = entered();
            double total = m.values().stream().mapToDouble(Double::doubleValue).sum();
            StringBuilder sb = new StringBuilder("<html><div style='width:580px'>");
            sb.append(String.format(GR, "<b>Σύνολο επένδυσης: %s</b><br>", Analysis.euro(total)));
            if (total > pos.spendableCash() + 0.005) {
                sb.append("<font color='#C62828'>✘ Δεν επαρκούν τα μετρητά του ταμείου.</font><br>");
            }
            List<String> v = LedgerLink.violations(base, pos, LedgerLink.after(pos, m));
            if (v.isEmpty()) {
                sb.append("<font color='#1E8E3E'>✔ Η νέα θέση ικανοποιεί όλους τους περιορισμούς του μοντέλου.</font>");
            } else {
                for (String s : v) sb.append("<font color='#E65100'>⚠ ").append(esc(s)).append("</font><br>");
            }
            warnings.setText(sb.append("</div></html>").toString());
        } catch (IllegalArgumentException ex) {
            warnings.setText("<html><font color='#C62828'>" + esc(ex.getMessage()) + "</font></html>");
        }
    }

    private void confirm() {
        Map<AssetClass, Double> m;
        try {
            m = entered();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double total = m.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            JOptionPane.showMessageDialog(this, "Δώστε ποσό επένδυσης μεγαλύτερο από 0.", "Επένδυση", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (total > pos.spendableCash() + 0.005) {
            JOptionPane.showMessageDialog(this, "Δεν επαρκούν τα μετρητά του ταμείου.", "Επένδυση", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> v = LedgerLink.violations(base, pos, LedgerLink.after(pos, m));
        if (!v.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Η επένδυση παραβιάζει περιορισμούς του μοντέλου:\n• " + String.join("\n• ", v) + "\n\nΝα γίνει παρ' όλα αυτά;",
                    "Επένδυση", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        Map<AssetClass, BigDecimal> purchases = new EnumMap<>(AssetClass.class);
        for (Map.Entry<AssetClass, Double> e : m.entrySet()) {
            purchases.put(e.getKey(), BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP));
        }
        dispose();
        onConfirm.accept(purchases);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
