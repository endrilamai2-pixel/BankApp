package bank.capitalInvestment;


    import bank.auth.Session;
import bank.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

    /**
     * Οθόνη της εφαρμογής: αριστερά τα στοιχεία της τράπεζας και οι ρυθμιστικοί περιορισμοί, στο κέντρο οι παραδοχές ανά
     * κατηγορία τοποθέτησης και από κάτω τα αποτελέσματα (βέλτιστη κατανομή, σύγκριση κινήσεων, τι περιορίζει την απόδοση).
     */
    public class BankPanel extends JPanel {

        private static final Locale GR = Locale.forLanguageTag("el-GR");
        private static final Color GREEN = new Color(0x1E8E3E);
        private static final Color RED = new Color(0xC62828);
        private static final Color ORANGE = new Color(0xE65100);

        // ---------- Στοιχεία τράπεζας ----------
        final AmountField funds = new AmountField("1000000000", false);
        final AmountField ownFunds = new AmountField("100000000", false);
        final JLabel depositsLabel = new JLabel();
        final JTextField depositCost = new JTextField();
        final JTextField overhead = new JTextField();
        final JTextField taxRate = new JTextField();

        // ---------- Ρυθμιστικοί περιορισμοί ----------
        final JTextField reserveRatio = new JTextField();
        final JTextField outflowRate = new JTextField();
        final JTextField minLcr = new JTextField();
        final JTextField capitalRatio = new JTextField();
        final JTextField leverageRatio = new JTextField();

        // ---------- Ανοχή κινδύνου και ακρίβεια ----------
        final JTextField varLimit = new JTextField();
        final JComboBox<String> stepBox = new JComboBox<>(new String[]{
                "10% (πολύ γρήγορο)", "5% (προτεινόμενο)", "2% (ακριβέστερο, πιο αργό)"});

        // ---------- Παραδοχές ανά κατηγορία ----------
        private static final String[] ASSUMPTION_COLUMNS = {"Κατηγορία", "Απόδοση %", "Αναμενόμενη ζημιά %",
                "Λειτουργικό κόστος %", "Μεταβλητότητα %", "Συντελεστής κινδύνου %", "Μέγιστο ποσοστό %"};
        final DefaultTableModel assumptionModel = new DefaultTableModel(ASSUMPTION_COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column > 0; }
        };
        final JTable assumptionTable = new JTable(assumptionModel);

        // ---------- Αποτελέσματα ----------
        final JLabel headline = new JLabel(" ");
        final DefaultTableModel allocationModel = new DefaultTableModel(
                new String[]{"Κατηγορία", "% των κεφαλαίων", "Ποσό", "Καθαρή απόδοση %"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        final DefaultTableModel comparisonModel = new DefaultTableModel(
                new String[]{"Κίνηση", "Κέρδος μετά φόρων", "ROE", "Κεφαλαιακή επάρκεια", "LCR", "Ζημιά 99% (% ιδίων)", "Κατάσταση"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        final JLabel metricsLabel = new JLabel(" ");
        final JLabel insightsLabel = new JLabel(" ");
        final JButton calculateBtn = new JButton("Υπολογισμός βέλτιστης κίνησης");
        final JButton loadLedgerBtn = new JButton("Φόρτωση κεφαλαίων από το καθολικό");
        final JButton investBtn = new JButton("Επένδυση κεφαλαίων…");
        /** Η βέλτιστη κατανομή του τελευταίου υπολογισμού (null αν δεν υπάρχει εφικτή). Χρησιμοποιείται για προτάσεις αγορών. */
        private double[] lastBestWeights;

        public BankPanel() {
            setLayout(new BorderLayout(12, 12));
            setBorder(new EmptyBorder(12, 12, 12, 12));

            JScrollPane inputs = new JScrollPane(buildInputs());
            inputs.setBorder(null);
            inputs.getVerticalScrollBar().setUnitIncrement(16);
            add(inputs, BorderLayout.WEST);
            add(buildCenter(), BorderLayout.CENTER);

            loadAssumptions(Assumptions.defaults());
            DocumentListener refreshDeposits = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { updateDeposits(); }
                public void removeUpdate(DocumentEvent e) { updateDeposits(); }
                public void changedUpdate(DocumentEvent e) { updateDeposits(); }
            };
            funds.getDocument().addDocumentListener(refreshDeposits);
            ownFunds.getDocument().addDocumentListener(refreshDeposits);
            updateDeposits();
        }

        // ===================== ΚΑΤΑΣΚΕΥΗ ΕΜΦΑΝΙΣΗΣ =====================

        private JPanel buildInputs() {
            JPanel form = new JPanel();
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

            JPanel bank = section("Στοιχεία τράπεζας");
            row(bank, 0, "Κεφάλαια προς τοποθέτηση (€)", funds);
            row(bank, 1, "Εκ των οποίων ίδια κεφάλαια (€)", ownFunds);
            row(bank, 2, "Καταθέσεις (υπολογίζονται)", depositsLabel);
            row(bank, 3, "Κόστος καταθέσεων (% ετησίως)", depositCost);
            row(bank, 4, "Γενικά λειτουργικά έξοδα (% ενεργητικού)", overhead);
            row(bank, 5, "Φορολογικός συντελεστής (%)", taxRate);

            JPanel law = section("Ρυθμιστικοί περιορισμοί (ΕΕ / ΕΚΤ)");
            row(law, 0, "Ελάχιστα αποθεματικά ΕΚΤ (% καταθέσεων)", reserveRatio);
            row(law, 1, "Εκροή καταθέσεων 30 ημερών (% για LCR)", outflowRate);
            row(law, 2, "Ελάχιστος LCR (%)", minLcr);
            row(law, 3, "Ελάχιστη κεφαλαιακή επάρκεια (%)", capitalRatio);
            row(law, 4, "Ελάχιστη μόχλευση (%)", leverageRatio);

            JPanel risk = section("Ανοχή κινδύνου");
            row(risk, 0, "Μέγιστη ζημιά 99% (% ιδίων κεφαλαίων)", varLimit);
            row(risk, 1, "Ακρίβεια αναζήτησης", stepBox);
            stepBox.setSelectedIndex(1);

            JPanel ledger = section("Σύνδεση με τη λογιστική της τράπεζας");
            JPanel ledgerButtons = new JPanel(new GridLayout(2, 1, 0, 6));
            ledgerButtons.add(loadLedgerBtn);
            ledgerButtons.add(investBtn);
            GridBagConstraints lc = new GridBagConstraints();
            lc.fill = GridBagConstraints.HORIZONTAL;
            lc.weightx = 1;
            ledger.add(ledgerButtons, lc);
            loadLedgerBtn.addActionListener(e -> loadFromLedger(false, null));
            investBtn.addActionListener(e -> openInvestDialog());

            Theme.primary(calculateBtn);
            calculateBtn.addActionListener(e -> calculate());
            JButton resetBtn = new JButton("Επαναφορά προεπιλογών");
            resetBtn.addActionListener(e -> loadAssumptions(Assumptions.defaults()));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttons.add(resetBtn);
            buttons.add(calculateBtn);

            JLabel disclaimer = new JLabel("<html><div style='width:330px'><font color='#B71C1C'><b>Εκπαιδευτικό μοντέλο.</b></font> "
                    + "Οι αποδόσεις, οι ζημίες και οι μεταβλητότητες είναι υποθέσεις και όχι προβλέψεις. Οι ρυθμιστικοί κανόνες "
                    + "(CRR/CRR3, LCR, ελάχιστα αποθεματικά ΕΚΤ) απλοποιούνται και οι πραγματικές απαιτήσεις διαφέρουν ανά τράπεζα. "
                    + "Δεν αποτελεί επενδυτική ή εποπτική συμβουλή.</div></html>");

            for (JComponent c : new JComponent[]{bank, ledger, law, risk, buttons, disclaimer}) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
                form.add(c);
                form.add(Box.createVerticalStrut(8));
            }
            return form;
        }

        private JPanel buildCenter() {
            JPanel center = new JPanel(new BorderLayout(8, 8));

            // Παραδοχές ανά κατηγορία
            Theme.styleTable(assumptionTable, 1, 2, 3, 4, 5, 6);
            assumptionTable.setRowHeight(26);
            assumptionTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            assumptionTable.getColumnModel().getColumn(0).setPreferredWidth(270);
            JScrollPane assumptionScroll = new JScrollPane(assumptionTable);
            assumptionScroll.setPreferredSize(new Dimension(900, 232));
            JPanel assumptionPanel = new JPanel(new BorderLayout(4, 4));
            assumptionPanel.setBorder(new TitledBorder("Παραδοχές ανά κατηγορία τοποθέτησης (επεξεργάσιμες)"));
            assumptionPanel.add(assumptionScroll, BorderLayout.CENTER);
            assumptionPanel.add(new JLabel("<html><div style='width:880px'><font color='gray'>Οι συντελεστές κινδύνου ακολουθούν την "
                    + "τυποποιημένη προσέγγιση του CRR/CRR3: κρατικά ομόλογα και μετρητά 0%, μετοχές 130% για το 2026 (250% από το 2030), "
                    + "στεγαστικά περίπου 35%, λιανική 75%, ΜμΕ 85%, εταιρικά δάνεια 100%. Το «μέγιστο ποσοστό» εκφράζει τη ζήτηση "
                    + "της αγοράς ή το όριο συγκέντρωσης που θέλει η τράπεζα.</font></div></html>"), BorderLayout.SOUTH);
            center.add(assumptionPanel, BorderLayout.NORTH);

            // Αποτελέσματα
            headline.setFont(headline.getFont().deriveFont(Font.BOLD, 15f));
            JTable allocationTable = new JTable(allocationModel);
            Theme.styleTable(allocationTable, 1, 2, 3);
            allocationTable.setRowHeight(26);
            allocationTable.getColumnModel().getColumn(0).setPreferredWidth(270);
            JPanel allocation = new JPanel(new BorderLayout(4, 4));
            allocation.add(new JScrollPane(allocationTable), BorderLayout.CENTER);
            allocation.add(metricsLabel, BorderLayout.SOUTH);
            ((JScrollPane) allocation.getComponent(0)).setPreferredSize(new Dimension(900, 215));

            JTable comparisonTable = new JTable(comparisonModel);
            comparisonTable.setRowHeight(26);
            comparisonTable.getColumnModel().getColumn(0).setPreferredWidth(260);
            comparisonTable.setDefaultRenderer(Object.class, new StatusRenderer());
            JPanel comparison = new JPanel(new BorderLayout());
            comparison.add(new JScrollPane(comparisonTable), BorderLayout.NORTH);

            JScrollPane insightsScroll = new JScrollPane(insightsLabel);
            insightsScroll.setBorder(null);
            insightsLabel.setVerticalAlignment(SwingConstants.TOP);
            insightsLabel.setBorder(new EmptyBorder(6, 6, 6, 6));

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Προτεινόμενη κατανομή", allocation);
            tabs.addTab("Σύγκριση κινήσεων", comparison);
            tabs.addTab("Τι περιορίζει την απόδοση", insightsScroll);
            tabs.setPreferredSize(new Dimension(900, 360));

            JPanel results = new JPanel(new BorderLayout(4, 6));
            results.setBorder(new TitledBorder("Αποτέλεσμα"));
            results.add(headline, BorderLayout.NORTH);
            results.add(tabs, BorderLayout.CENTER);
            center.add(results, BorderLayout.CENTER);
            return center;
        }

        private static JPanel section(String title) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBorder(BorderFactory.createCompoundBorder(new TitledBorder(title), new EmptyBorder(2, 6, 6, 6)));
            return p;
        }

        private static void row(JPanel p, int row, String label, Component field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0;
            p.add(new JLabel(label), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(field, c);
        }

        // ===================== ΦΟΡΤΩΣΗ / ΑΝΑΓΝΩΣΗ ΠΑΡΑΜΕΤΡΩΝ =====================

        void loadAssumptions(Assumptions a) {
            funds.setText(String.valueOf((long) a.funds));
            ownFunds.setText(String.valueOf((long) a.ownFunds));
            set(depositCost, a.depositCost); set(overhead, a.overhead); set(taxRate, a.taxRate);
            set(reserveRatio, a.reserveRatio); set(outflowRate, a.outflowRate); set(minLcr, a.minLcr);
            set(capitalRatio, a.capitalRatio); set(leverageRatio, a.leverageRatio); set(varLimit, a.varLimit);

            assumptionModel.setRowCount(0);
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                assumptionModel.addRow(new Object[]{c.label, num(a.expectedReturn[i]), num(a.expectedLoss[i]), num(a.opCost[i]),
                        num(a.volatility[i]), num(a.riskWeight[i]), num(a.maxShare[i])});
            }
        }

        /** Διαβάζει όλα τα πεδία. Ρίχνει IllegalArgumentException με κατανοητό μήνυμα αν κάτι δεν είναι αριθμός. */
        Assumptions readAssumptions() {
            if (assumptionTable.isEditing()) assumptionTable.getCellEditor().stopCellEditing();
            Assumptions a = Assumptions.defaults();
            a.funds = amount(funds, "Κεφάλαια προς τοποθέτηση");
            a.ownFunds = amount(ownFunds, "Ίδια κεφάλαια");
            a.depositCost = parse(depositCost, "Κόστος καταθέσεων");
            a.overhead = parse(overhead, "Γενικά λειτουργικά έξοδα");
            a.taxRate = parse(taxRate, "Φορολογικός συντελεστής");
            a.reserveRatio = parse(reserveRatio, "Ελάχιστα αποθεματικά ΕΚΤ");
            a.outflowRate = parse(outflowRate, "Εκροή καταθέσεων");
            a.minLcr = parse(minLcr, "Ελάχιστος LCR");
            a.capitalRatio = parse(capitalRatio, "Ελάχιστη κεφαλαιακή επάρκεια");
            a.leverageRatio = parse(leverageRatio, "Ελάχιστη μόχλευση");
            a.varLimit = parse(varLimit, "Μέγιστη ζημιά 99%");

            double[][] columns = {a.expectedReturn, a.expectedLoss, a.opCost, a.volatility, a.riskWeight, a.maxShare};
            for (AssetClass c : AssetClass.values()) {
                for (int col = 0; col < columns.length; col++) {
                    columns[col][c.ordinal()] = parseCell(assumptionModel.getValueAt(c.ordinal(), col + 1),
                            c.label + " / " + ASSUMPTION_COLUMNS[col + 1]);
                }
            }
            return a;
        }

        int selectedStep() {
            return switch (stepBox.getSelectedIndex()) {
                case 0 -> 10;
                case 2 -> 2;
                default -> 5;
            };
        }

        private void updateDeposits() {
            double f = funds.getAmount(), o = ownFunds.getAmount();
            depositsLabel.setText(Double.isNaN(f) || Double.isNaN(o) ? "—" : Analysis.euro(f - o));
        }

        // ===================== ΥΠΟΛΟΓΙΣΜΟΣ =====================

        void calculate() {
            final Assumptions a;
            try {
                a = readAssumptions();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int step = selectedStep();
            calculateBtn.setEnabled(false);
            headline.setForeground(UIManager.getColor("Label.foreground"));
            headline.setText("Υπολογισμός...");
            new SwingWorker<Analysis.Report, Void>() {
                @Override
                protected Analysis.Report doInBackground() {
                    return Analysis.run(a, step);
                }

                @Override
                protected void done() {
                    calculateBtn.setEnabled(true);
                    try {
                        applyReport(get(), a);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        headline.setForeground(RED);
                        headline.setText("Σφάλμα υπολογισμού: " + ex.getCause());
                    }
                }
            }.execute();
        }

        /** Εμφανίζει το αποτέλεσμα στις τρεις καρτέλες. */
        void applyReport(Analysis.Report r, Assumptions a) {
            allocationModel.setRowCount(0);
            comparisonModel.setRowCount(0);
            metricsLabel.setText(" ");

            if (!r.hasResults()) {
                lastBestWeights = null;
                headline.setForeground(RED);
                headline.setText("Δεν υπάρχει εφικτή κίνηση με αυτούς τους περιορισμούς.");
                insightsLabel.setText(htmlList(r.problems(), "#B71C1C"));
                fillComparison(r);
                return;
            }

            Strategy best = r.best().orElseThrow();
            PortfolioOptimizer.Solution sol = r.results().get(best).orElseThrow();
            Metrics m = sol.metrics();
            lastBestWeights = sol.weights().clone();

            headline.setForeground(GREEN);
            headline.setText(String.format(GR, "Καλύτερη κίνηση: %s. Καθαρό κέρδος %s (ROE %.1f%%). %s",
                    best.label, Analysis.euro(m.netProfit()), m.roe(), groupSummary(sol.weights())));

            // Κατανομή
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                double w = sol.weights()[i];
                double net = a.expectedReturn[i] - a.expectedLoss[i] - a.opCost[i];
                allocationModel.addRow(new Object[]{c.label,
                        w > 1e-9 ? String.format(GR, "%.0f%%", w * 100) : "—",
                        w > 1e-9 ? Analysis.euro(m.amounts()[i]) : "—",
                        String.format(GR, "%.2f%%", net)});
            }

            metricsLabel.setText("<html><div style='width:860px'>" + metricsHtml(a, m) + "</div></html>");
            fillComparison(r);

            java.util.List<String> lines = new java.util.ArrayList<>();
            for (Analysis.Insight in : r.insights()) lines.add(in.text());
            if (lines.isEmpty()) lines.add("Κανένας περιορισμός δεν δεσμεύει πλήρως στη βέλτιστη λύση.");
            insightsLabel.setText("<html><div style='width:820px'><b>Τι κρατά το κέρδος χαμηλότερα, με σειρά σημασίας:</b><br>"
                    + htmlList(lines, "#333333").replace("<html>", "").replace("</html>", "") + "</div></html>");
        }

        private void fillComparison(Analysis.Report r) {
            Optional<Strategy> best = r.best();
            for (Strategy s : Strategy.values()) {
                Optional<PortfolioOptimizer.Solution> res = r.results().get(s);
                if (res == null || res.isEmpty()) {
                    comparisonModel.addRow(new Object[]{s.label, "—", "—", "—", "—", "—", "Αδύνατη με αυτούς τους περιορισμούς"});
                } else {
                    Metrics m = res.get().metrics();
                    comparisonModel.addRow(new Object[]{s.label, Analysis.euro(m.netProfit()),
                            String.format(GR, "%.1f%%", m.roe()), String.format(GR, "%.1f%%", m.capitalRatio()),
                            String.format(GR, "%.0f%%", m.lcr()), String.format(GR, "%.0f%%", m.varPctOfOwnFunds()),
                            best.isPresent() && best.get() == s ? "Η ΚΑΛΥΤΕΡΗ" : "Εφικτή"});
                }
            }
        }

        private String metricsHtml(Assumptions a, Metrics m) {
            return String.format(GR,
                    "<b>Ρυθμιστικοί έλεγχοι (όλοι πρέπει να ισχύουν):</b><br>"
                            + "• Ελάχιστα αποθεματικά ΕΚΤ: κρατά %s στην κεντρική τράπεζα, υποχρεωτικό ελάχιστο %s %s<br>"
                            + "• Ρευστότητα LCR: %.0f%% (ελάχιστο %.0f%%), με ρευστά διαθέσιμα (μετρητά και κρατικά ομόλογα) ανάγκη τουλάχιστον %s<br>"
                            + "• Κεφαλαιακή επάρκεια: %s (ελάχιστο %.1f%%), σταθμισμένα στοιχεία %s από μέγιστο %s<br>"
                            + "• Μόχλευση: %.1f%% (ελάχιστο %.1f%%)<br>"
                            + "<b>Κίνδυνος:</b> τυπική απόκλιση %.2f%% του ενεργητικού. Ζημιά 99%% ενός έτους %s, δηλαδή %.0f%% των ιδίων κεφαλαίων (όριο %.0f%%).",
                    Analysis.euro(m.cash()), Analysis.euro(m.minReserve()), ok(m.cash() >= m.minReserve() - 1),
                    m.lcr(), a.minLcr, Analysis.euro(a.minHqlaAmount()),
                    Double.isInfinite(m.capitalRatio()) ? "χωρίς κίνδυνο" : String.format(GR, "%.1f%%", m.capitalRatio()),
                    a.capitalRatio, Analysis.euro(m.rwa()), Analysis.euro(m.maxRwa()),
                    m.leverageRatio(), a.leverageRatio,
                    m.sigma(), Analysis.euro(m.var99()), m.varPctOfOwnFunds(), a.varLimit);
        }

        private static String ok(boolean b) {
            return b ? "<font color='#1E8E3E'>✔</font>" : "<font color='#C62828'>✘</font>";
        }

        private static String groupSummary(double[] w) {
            double liquid = 0, markets = 0, lending = 0;
            for (AssetClass c : AssetClass.values()) {
                double v = w[c.ordinal()];
                switch (c.group) {
                    case LIQUIDITY -> liquid += v;
                    case MARKETS -> markets += v;
                    case LENDING -> lending += v;
                }
            }
            return String.format(GR, "Κατανομή: δανεισμός %.0f%%, μετοχές και εταιρικά ομόλογα %.0f%%, ρευστότητα (μετρητά και κρατικά ομόλογα) %.0f%%.",
                    lending * 100, markets * 100, liquid * 100);
        }

        private static String htmlList(java.util.List<String> items, String color) {
            StringBuilder sb = new StringBuilder("<html><div style='width:820px'><font color='" + color + "'>");
            for (String s : items) sb.append("• ").append(esc(s)).append("<br>");
            return sb.append("</font></div></html>").toString();
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        // ===================== ΣΥΝΔΕΣΗ ΜΕ ΤΟ ΚΑΘΟΛΙΚΟ =====================

        /**
         * Διαβάζει από το καθολικό το συνολικό ενεργητικό και τα ίδια κεφάλαια και τα βάζει στα πεδία της οθόνης.
         * @param quiet αν true δεν εμφανίζονται μηνύματα (χρησιμοποιείται στο άνοιγμα της οθόνης)
         * @param afterLoad εκτελείται όταν τελειώσει η φόρτωση, είτε πέτυχε είτε όχι (μπορεί να είναι null)
         */
        void loadFromLedger(boolean quiet, Runnable afterLoad) {
            loadLedgerBtn.setEnabled(false);
            new SwingWorker<LedgerLink.Position, Void>() {
                @Override protected LedgerLink.Position doInBackground() throws Exception { return LedgerLink.load(); }
                @Override protected void done() {
                    loadLedgerBtn.setEnabled(true);
                    try {
                        LedgerLink.Position p = get();
                        if (p.totalAssets() <= 0 || p.ownFunds() <= 0 || p.deposits() <= 0) {
                            if (!quiet) {
                                JOptionPane.showMessageDialog(BankPanel.this,
                                        "Στο καθολικό δεν υπάρχουν ακόμη ενεργητικό, καταθέσεις και ίδια κεφάλαια.\n"
                                      + "Καταχωρήστε εισφορά κεφαλαίου από τη «Λογιστική» και κινήσεις πελατών.",
                                        "Φόρτωση από το καθολικό", JOptionPane.INFORMATION_MESSAGE);
                            }
                        } else {
                            funds.setText(String.valueOf(Math.round(p.totalAssets())));
                            ownFunds.setText(String.valueOf(Math.round(p.ownFunds())));
                            if (!quiet) {
                                JOptionPane.showMessageDialog(BankPanel.this, String.format(GR,
                                        "Φορτώθηκαν από το καθολικό:%nΕνεργητικό %s, ίδια κεφάλαια %s, καταθέσεις %s.",
                                        Analysis.euro(p.totalAssets()), Analysis.euro(p.ownFunds()), Analysis.euro(p.deposits())),
                                        "Φόρτωση από το καθολικό", JOptionPane.INFORMATION_MESSAGE);
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        if (!quiet) {
                            JOptionPane.showMessageDialog(BankPanel.this, "Αδυναμία ανάγνωσης του καθολικού:\n" + ex.getCause().getMessage(),
                                    "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                        }
                    } finally {
                        if (afterLoad != null) afterLoad.run();
                    }
                }
            }.execute();
        }

        /** Ανοίγει το παράθυρο επένδυσης με τη σημερινή θέση της τράπεζας από το καθολικό. */
        void openInvestDialog() {
            final Assumptions base;
            try {
                base = readAssumptions();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
                return;
            }
            investBtn.setEnabled(false);
            new SwingWorker<LedgerLink.Position, Void>() {
                @Override protected LedgerLink.Position doInBackground() throws Exception { return LedgerLink.load(); }
                @Override protected void done() {
                    investBtn.setEnabled(true);
                    try {
                        LedgerLink.Position p = get();
                        if (p.spendableCash() <= 0) {
                            JOptionPane.showMessageDialog(BankPanel.this,
                                    "Το ταμείο δεν έχει διαθέσιμα μετρητά για επένδυση.\n"
                                  + "Καταχωρήστε εισφορά κεφαλαίου ή καταθέσεις από τη «Λογιστική».",
                                    "Επένδυση κεφαλαίων", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }
                        Map<AssetClass, Double> suggestion = lastBestWeights == null
                                ? Map.of() : LedgerLink.suggest(p, lastBestWeights);
                        new InvestDialog(SwingUtilities.getWindowAncestor(BankPanel.this), p, base, suggestion,
                                BankPanel.this::executeInvestment).setVisible(true);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        JOptionPane.showMessageDialog(BankPanel.this, "Αδυναμία ανάγνωσης του καθολικού:\n" + ex.getCause().getMessage(),
                                "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private void executeInvestment(Map<AssetClass, java.math.BigDecimal> purchases) {
            investBtn.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    LedgerLink.invest(purchases, Session.currentUser());
                    return null;
                }
                @Override protected void done() {
                    investBtn.setEnabled(true);
                    try {
                        get();
                        JOptionPane.showMessageDialog(BankPanel.this,
                                "Η επένδυση καταχωρήθηκε στο καθολικό (μετρητά → χρεόγραφα). Δείτε τη «Λογιστική».",
                                "Επένδυση κεφαλαίων", JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        JOptionPane.showMessageDialog(BankPanel.this, "Η επένδυση δεν καταχωρήθηκε:\n" + ex.getCause().getMessage(),
                                "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        // ===================== ΒΟΗΘΗΤΙΚΑ ΜΟΡΦΟΠΟΙΗΣΗΣ / ΑΝΑΓΝΩΣΗΣ =====================

        /** 10.5 -> «10,5», 2.0 -> «2». */
        private static String num(double v) {
            return new BigDecimal(String.valueOf(v)).stripTrailingZeros().toPlainString().replace('.', ',');
        }

        private static void set(JTextField f, double v) {
            f.setText(num(v));
        }

        private static double parse(JTextField f, String name) {
            try {
                return Double.parseDouble(f.getText().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Μη έγκυρη τιμή στο πεδίο «" + name + "».");
            }
        }

        private static double parseCell(Object value, String name) {
            try {
                return Double.parseDouble(String.valueOf(value).trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Μη έγκυρη τιμή στο πεδίο «" + name + "».");
            }
        }

        private static double amount(AmountField f, String name) {
            double v = f.getAmount();
            if (Double.isNaN(v)) throw new IllegalArgumentException("Μη έγκυρο ποσό στο πεδίο «" + name + "».");
            return v;
        }

        /** Χρωματίζει τη σειρά ανάλογα με την κατάσταση της κίνησης. */
        private static class StatusRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                String status = String.valueOf(table.getModel().getValueAt(row, 6));
                if (!selected) {
                    if (status.startsWith("Η ΚΑΛΥΤΕΡΗ")) setForeground(GREEN);
                    else if (status.startsWith("Αδύνατη")) setForeground(RED);
                    else setForeground(UIManager.getColor("Table.foreground"));
                }
                setFont(getFont().deriveFont(status.startsWith("Η ΚΑΛΥΤΕΡΗ") ? Font.BOLD : Font.PLAIN));
                return this;
            }
        }
    }


