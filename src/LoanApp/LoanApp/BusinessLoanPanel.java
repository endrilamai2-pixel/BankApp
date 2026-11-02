package LoanApp.LoanApp;

import bank.account.AccountDAO;
import bank.account.ContractService;
import bank.account.Disbursement;
import bank.account.DisbursementDialog;
import bank.account.LoanTerms;
import bank.auth.Session;
import bank.registration.BusinessCustomer;
import bank.registration.CustomerDAO;
import bank.registration.CustomerInfoDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Οθόνη αίτησης δανείου για επιχειρήσεις.
 * Η κατηγορία μεγέθους (Ν. 4308/2014 - ΕΛΠ) και ο πιστοληπτικός βαθμός ενημερώνονται ζωντανά.
 */
public class BusinessLoanPanel extends JPanel {

    /** Πόσα pixels πλάτος προστίθενται στο αριστερό τμήμα (μεγαλώστε το για πιο φαρδιά πεδία). */
    static final int FORM_EXTRA_WIDTH = 140;

    // ---------- Στοιχεία επιχείρησης ----------
    final JButton loadStatementBtn = new JButton("Φόρτωση οικονομικών καταστάσεων (PDF / TXT / CSV)...");
    final JLabel statementInfo = new JLabel(" ");
    private static final Color FILLED_BG = new Color(0xDDF3E1);     // πράσινο φόντο στα πεδία που συμπληρώθηκαν αυτόματα
    private final Set<JTextField> autoFilled = new HashSet<>();
    private Color defaultFieldBg;
    final JTextField taxIdField = new JTextField(10);
    final JButton lookupBtn = new JButton("Αναζήτηση");
    final JLabel registryInfo = new JLabel(" ");
    final JLabel customerInfo = new JLabel(" ");
    final CustomerDAO customerDao = new CustomerDAO();

    // ---------- Εκταμίευση ----------
    final JButton disburseBtn = new JButton("Εκταμίευση δανείου στον λογαριασμό της επιχείρησης");
    final JLabel disburseNote = new JLabel(" ");
    BusinessCustomer foundCustomer;        // η επιχείρηση της τελευταίας επιτυχούς αναζήτησης (ΑΦΜ)
    BusinessApplication lastApp;           // η τελευταία αξιολόγηση
    Main.Decision lastDecision;
    boolean disbursed;                     // έχει ήδη εκταμιευτεί η τελευταία αξιολόγηση
    final CompanyRegistryService registry = AadeCompanyRegistry.create();
    CompanyRecord registryRecord;          // το αποτέλεσμα της τελευταίας επιτυχούς αναζήτησης
    final JTextField companyName = new JTextField(16);
    final JTextField yearsInOperation = new JTextField("0");
    final JTextField avgEmployees = new JTextField("0");

    // ---------- Οικονομικά στοιχεία (ισολογισμός / αποτελέσματα ΕΛΠ) ----------
    final JTextField totalAssets = new MoneyField("0", false);
    final JTextField netTurnover = new MoneyField("0", false);
    final JTextField equity = new MoneyField("0", true);
    final JTextField currentAssets = new MoneyField("0", false);
    final JTextField currentLiabilities = new MoneyField("0", false);
    final JTextField ebitda = new MoneyField("0", true);
    final JTextField netProfit = new MoneyField("0", true);
    final JTextField interestExpense = new MoneyField("0", false);
    final JTextField existingDebtService = new MoneyField("0", false);

    // ---------- Συμπεριφορά πληρωμών ----------
    final JSpinner latePayments = LoanPanel.intSpinner(0, 100, 0);
    final JSpinner bouncedCheques = LoanPanel.intSpinner(0, 100, 0);
    final JCheckBox arrears = new JCheckBox("Ναι");
    final JCheckBox insolvency = new JCheckBox("Ναι");

    // ---------- Δάνειο ----------
    final JTextField amount = new MoneyField("", false);
    final JSpinner term = LoanPanel.intSpinner(1, LoanAppParameters.MAX_TERM_YEARS, 7);
    final JComboBox<BusinessPurpose> purpose = new JComboBox<>(BusinessPurpose.values());

    // ---------- Αποτέλεσμα ----------
    final JLabel categoryLabel = new JLabel(" ");
    final JLabel categoryCriteria = new JLabel(" ");
    final JProgressBar scoreBar = new JProgressBar(CreditScoreCalculator.MIN_SCORE, CreditScoreCalculator.MAX_SCORE);
    final JLabel scoreBreakdown = new JLabel(" ");
    final JLabel decisionLabel = new JLabel(" ");
    final JLabel detailsLabel = new JLabel();

    public BusinessLoanPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        defaultFieldBg = totalAssets.getBackground();

        purpose.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof BusinessPurpose p) setText(p.label);
                return this;
            }
        });

        JPanel form = buildForm();
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        // Το αριστερό τμήμα παίρνει το πλήρες ύψος της φόρμας, ώστε να φαίνονται όλα τα πεδία χωρίς κύλιση.
        // Μόνο αν η οθόνη είναι πολύ χαμηλή περιορίζεται (και τότε εμφανίζεται μπάρα κύλισης).
        formScroll.setPreferredSize(new Dimension(form.getPreferredSize().width + 28 + FORM_EXTRA_WIDTH,
                heightThatFits(form.getPreferredSize().height)));
        add(formScroll, BorderLayout.WEST);
        add(buildResultPanel(), BorderLayout.CENTER);

        // Ζωντανή ενημέρωση κατηγορίας ΕΛΠ και πιστοληπτικού βαθμού
        for (JTextField f : new JTextField[]{totalAssets, netTurnover, avgEmployees, equity, currentAssets,
                currentLiabilities, ebitda, netProfit, interestExpense, existingDebtService, yearsInOperation}) {
            LoanPanel.onTextChange(f, this::updatePreview);
        }
        for (JSpinner s : new JSpinner[]{latePayments, bouncedCheques}) {
            s.addChangeListener(e -> updatePreview());
        }
        arrears.addActionListener(e -> updatePreview());
        // Αν αλλάξει το ΑΦΜ μετά την αναζήτηση, η επιχείρηση δεν θεωρείται πλέον επιβεβαιωμένη
        LoanPanel.onTextChange(taxIdField, () -> {
            if (foundCustomer != null && !foundCustomer.afm().equals(taxIdField.getText().trim())) {
                foundCustomer = null;
                refreshDisburse();
            }
        });
        insolvency.addActionListener(e -> updatePreview());
        showRegistryInfo("<font color='gray'>Πηγή δεδομένων: " + LoanPanel.esc(registry.sourceName()) + "</font>");
        updatePreview();
        showPlaceholder();
    }

    // ===================== ΚΑΤΑΣΚΕΥΗ ΕΜΦΑΝΙΣΗΣ =====================

    private JPanel buildForm() {
        JPanel form = new ScrollableForm();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel company = LoanPanel.section("Στοιχεία επιχείρησης");
        lookupBtn.addActionListener(e -> lookupRegistry());
        JPanel taxPanel = new JPanel(new BorderLayout(6, 0));
        taxPanel.add(taxIdField, BorderLayout.CENTER);
        taxPanel.add(lookupBtn, BorderLayout.EAST);
        LoanPanel.addRow(company, 0, "ΑΦΜ", taxPanel);
        taxIdField.addActionListener(e -> lookupRegistry());     // Enter μέσα στο πεδίο ΑΦΜ
        LoanPanel.addRow(company, 1, "", registryInfo);
        LoanPanel.addRow(company, 2, "", customerInfo);
        LoanPanel.addRow(company, 3, "Επωνυμία", companyName);
        LoanPanel.addRow(company, 4, "Έτη λειτουργίας", yearsInOperation);
        LoanPanel.addRow(company, 5, "Μέσος όρος απασχολουμένων", avgEmployees);

        JPanel fin = LoanPanel.section("Οικονομικά στοιχεία (ΕΛΠ, τελευταία χρήση)");
        loadStatementBtn.addActionListener(e -> loadStatement());
        LoanPanel.addRow(fin, 0, "", loadStatementBtn);
        LoanPanel.addRow(fin, 1, "Σύνολο ενεργητικού (€)", totalAssets);
        LoanPanel.addRow(fin, 2, "Καθαρός κύκλος εργασιών (€)", netTurnover);
        LoanPanel.addRow(fin, 3, "Ίδια κεφάλαια (€)", equity);
        LoanPanel.addRow(fin, 4, "Κυκλοφορούν ενεργητικό (€)", currentAssets);
        LoanPanel.addRow(fin, 5, "Βραχυπρόθεσμες υποχρεώσεις (€)", currentLiabilities);
        LoanPanel.addRow(fin, 6, "EBITDA (€)", ebitda);
        LoanPanel.addRow(fin, 7, "Καθαρά αποτελέσματα χρήσης (€)", netProfit);
        LoanPanel.addRow(fin, 8, "Χρεωστικοί τόκοι (€)", interestExpense);
        LoanPanel.addRow(fin, 9, "Ετήσιες δόσεις υφιστάμενων δανείων (€)", existingDebtService);

        JPanel behaviour = LoanPanel.section("Συμπεριφορά πληρωμών");
        LoanPanel.addRow(behaviour, 0, "Καθυστερήσεις 30+ ημερών (2 έτη)", latePayments);
        LoanPanel.addRow(behaviour, 1, "Ακάλυπτες επιταγές / απλήρωτες συν/κές", bouncedCheques);
        LoanPanel.addRow(behaviour, 2, "Ληξιπρόθεσμα προς ΑΑΔΕ / ΕΦΚΑ", arrears);
        LoanPanel.addRow(behaviour, 3, "Πτώχευση / εξυγίανση / συνδιαλλαγή", insolvency);

        JPanel loan = LoanPanel.section("Στοιχεία δανείου");
        LoanPanel.addRow(loan, 0, "Ποσό δανείου (€)", amount);
        LoanPanel.addRow(loan, 1, "Διάρκεια (έτη)", term);
        LoanPanel.addRow(loan, 2, "Σκοπός δανείου", purpose);

        JButton evaluateBtn = new JButton("Αξιολόγηση αίτησης");
        evaluateBtn.setFont(evaluateBtn.getFont().deriveFont(Font.BOLD, 14f));
        evaluateBtn.addActionListener(e -> evaluate());
        JButton clearBtn = new JButton("Καθαρισμός");
        clearBtn.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(clearBtn);
        buttons.add(evaluateBtn);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (JPanel p : new JPanel[]{company, fin, behaviour, loan}) {
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(p);
            form.add(Box.createVerticalStrut(8));
        }
        form.add(buttons);

        // Το Enter ενεργοποιεί το κουμπί όταν η καρτέλα είναι ορατή
        addHierarchyListener(e -> {
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null && isShowing()) root.setDefaultButton(evaluateBtn);
        });
        return form;
    }

    private JPanel buildResultPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Αποτέλεσμα"));

        JLabel categoryTitle = new JLabel("Κατηγορία μεγέθους (ΕΛΠ - Ν. 4308/2014)");
        categoryTitle.setFont(categoryTitle.getFont().deriveFont(Font.BOLD));
        categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD, 20f));
        categoryCriteria.setForeground(Color.GRAY);

        JLabel scoreTitle = new JLabel("Εκτιμώμενος πιστοληπτικός βαθμός (ζωντανά)");
        scoreTitle.setFont(scoreTitle.getFont().deriveFont(Font.BOLD));
        scoreBar.setStringPainted(true);
        scoreBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        decisionLabel.setFont(decisionLabel.getFont().deriveFont(Font.BOLD, 26f));
        decisionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        detailsLabel.setVerticalAlignment(SwingConstants.TOP);

        for (JComponent c : new JComponent[]{categoryTitle, categoryLabel, categoryCriteria, scoreTitle,
                scoreBar, scoreBreakdown, statementInfo, decisionLabel, detailsLabel}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(Box.createVerticalStrut(6));
        panel.add(categoryTitle);
        panel.add(Box.createVerticalStrut(4));
        panel.add(categoryLabel);
        panel.add(categoryCriteria);
        panel.add(Box.createVerticalStrut(14));
        panel.add(scoreTitle);
        panel.add(Box.createVerticalStrut(6));
        panel.add(scoreBar);
        panel.add(Box.createVerticalStrut(4));
        panel.add(scoreBreakdown);
        panel.add(Box.createVerticalStrut(10));
        panel.add(statementInfo);
        panel.add(Box.createVerticalStrut(14));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(14));
        panel.add(decisionLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(detailsLabel);
        panel.add(Box.createVerticalStrut(10));
        disburseNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        disburseBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        disburseBtn.setFont(disburseBtn.getFont().deriveFont(Font.BOLD));
        disburseBtn.setVisible(false);
        disburseBtn.addActionListener(e -> disburse());
        panel.add(disburseNote);
        panel.add(Box.createVerticalStrut(4));
        panel.add(disburseBtn);
        panel.add(Box.createVerticalGlue());
        panel.setPreferredSize(new Dimension(500, 700));
        return panel;
    }

    /** Φόρμα που προσαρμόζει το πλάτος της στο πλάτος του scroll pane, ώστε τα πεδία να γεμίζουν τον χώρο. */
    private static class ScrollableForm extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(16, r.height - 16);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Το ύψος που χρειάζεται η φόρμα, περιορισμένο στο διαθέσιμο ύψος της οθόνης. */
    private static int heightThatFits(int wanted) {
        int available = 1000;                                      // προεπιλογή (π.χ. χωρίς οθόνη)
        if (!GraphicsEnvironment.isHeadless()) {
            // ύψος οθόνης χωρίς taskbar, μείον τίτλο παραθύρου, καρτέλες και περιθώρια
            available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height - 130;
        }
        return Math.min(wanted + 8, available);
    }

    // ===================== ΕΝΕΡΓΕΙΕΣ =====================

    /** Πατήθηκε το κουμπί «Αξιολόγηση αίτησης». */
    void evaluate() {
        try {
            String name = companyName.getText().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Συμπληρώστε την επωνυμία της επιχείρησης.");

            BusinessScoreCalculator.BusinessFinancials fin = readFinancials();
            BusinessScoreCalculator.BusinessScoreResult sr = BusinessScoreCalculator.calculate(fin);
            EntityCategory category = EntityCategory.classify(
                    fin.totalAssets(), fin.netTurnover(), fin.avgEmployees());

            double amountValue = LoanPanel.parseNumber(amount, "Ποσό δανείου", 1);
            int termValue = LoanPanel.intValue(term);
            BusinessPurpose p = (BusinessPurpose) purpose.getSelectedItem();

            BusinessApplication app = new BusinessApplication(
                    name, fin, sr.score(), category, amountValue, termValue, p);
            // Αν έχει γίνει αναζήτηση στο μητρώο για ΑΛΛΟ ΑΦΜ από το τρέχον, η αναζήτηση θεωρείται παλιά
            if (registryRecord != null && !registryRecord.taxId().equals(taxIdField.getText().trim())) {
                registryRecord = null;
            }
            Main.Decision d = (registryRecord != null && !registryRecord.isActive())
                    ? Main.reject(CreditTier.fromScore(sr.score()),
                    "Το ΑΦΜ δεν είναι ενεργό στην ΑΑΔΕ (κατάσταση: " + registryRecord.status() + ").")
                    : BusinessLoanLogic.evaluate(app);
            lastApp = app;
            lastDecision = d;
            disbursed = false;
            showResult(app, sr, d);
            refreshDisburse();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showResult(BusinessApplication app, BusinessScoreCalculator.BusinessScoreResult sr,
                            Main.Decision d) {
        StringBuilder html = new StringBuilder("<html><table cellpadding='3'>");
        LoanPanel.row(html, "Επιχείρηση", LoanPanel.esc(app.companyName()));
        LoanPanel.row(html, "Έλεγχος ΑΑΔΕ", registryRecord == null
                ? "<i>δεν έγινε αναζήτηση</i>"
                : LoanPanel.esc(registryRecord.status() + " (ΑΦΜ " + registryRecord.taxId() + ")"));
        LoanPanel.row(html, "Κατηγορία ΕΛΠ", app.category().label);
        LoanPanel.row(html, "Πιστοληπτικός βαθμός", sr.score() + " (" + d.tier().label + ")");
        LoanPanel.row(html, "Σκοπός", app.purpose().label);
        LoanPanel.row(html, "Ποσό", LoanPanel.money(app.amount()));
        LoanPanel.row(html, "Διάρκεια", app.termYears() + " έτη");
        LoanPanel.row(html, "Ίδια κεφάλαια / Ενεργητικό", String.format(LoanPanel.GR, "%.1f%%", sr.equityRatio() * 100));
        LoanPanel.row(html, "Τρέχουσα ρευστότητα", Double.isNaN(sr.currentRatio())
                ? "— <i>(δεν δόθηκε κυκλοφορούν ενεργητικό, ουδέτερη βαθμολογία)</i>"
                : String.format(LoanPanel.GR, "%.2f", sr.currentRatio()));
        LoanPanel.row(html, "Περιθώριο καθαρού κέρδους", String.format(LoanPanel.GR, "%.1f%%", sr.netMargin() * 100));
        LoanPanel.row(html, "Κάλυψη τόκων (EBITDA/τόκοι)",
                Double.isNaN(sr.interestCoverage()) ? "—" : String.format(LoanPanel.GR, "%.1f", sr.interestCoverage()));

        if (d.approved()) {
            decisionLabel.setText("✔ ΕΓΚΡΙΣΗ");
            decisionLabel.setForeground(LoanPanel.GREEN);
            double dscr = BusinessLoanLogic.debtServiceCoverage(app, d.monthlyPayment() * 12);
            LoanPanel.row(html, "Επιτόκιο", String.format(LoanPanel.GR, "%.2f%%", d.rate()));
            LoanPanel.row(html, "Μηνιαία δόση", "<b>" + LoanPanel.money(d.monthlyPayment()) + "</b>");
            LoanPanel.row(html, "Συνολικό κόστος", LoanPanel.money(d.totalCost()));
            LoanPanel.row(html, "Συνολικοί τόκοι", LoanPanel.money(d.totalCost() - app.amount()));
            LoanPanel.row(html, "DSCR μετά το δάνειο", String.format(LoanPanel.GR, "%.2f", dscr));
        } else {
            decisionLabel.setText("✘ ΑΠΟΡΡΙΨΗ");
            decisionLabel.setForeground(LoanPanel.RED);
            LoanPanel.row(html, "Λόγος", "<div style='width:280px'>" + LoanPanel.esc(d.reason()) + "</div>");
        }
        html.append("</table></html>");
        detailsLabel.setText(html.toString());
    }

    // ===================== ΕΚΤΑΜΙΕΥΣΗ ΔΑΝΕΙΟΥ =====================

    /** Το κουμπί εκταμίευσης εμφανίζεται μόνο για εγκεκριμένη αίτηση υπάρχουσας (επιβεβαιωμένης με ΑΦΜ) επιχείρησης. */
    private void refreshDisburse() {
        boolean approved = lastDecision != null && lastDecision.approved();
        boolean eligible = approved && !disbursed && foundCustomer != null
                && foundCustomer.afm().equals(taxIdField.getText().trim());
        disburseBtn.setVisible(eligible);
        disburseBtn.setEnabled(eligible);
        if (disbursed) {
            disburseNote.setText("<html><font color='" + hex(LoanPanel.GREEN) + "'><b>Το δάνειο εκταμιεύθηκε και πιστώθηκε στον λογαριασμό.</b></font></html>");
        } else if (approved && !eligible) {
            disburseNote.setText("<html><font color='" + hex(LoanPanel.ORANGE) + "'>Για να πιστωθεί το δάνειο, κάνε πρώτα αναζήτηση "
                    + "υπάρχουσας επιχείρησης με το ΑΦΜ της.</font></html>");
        } else {
            disburseNote.setText(" ");
        }
    }

    /** Πατήθηκε «Εκταμίευση»: πιστώνει το ποσό του εγκεκριμένου δανείου στον λογαριασμό της επιχείρησης. */
    void disburse() {
        if (lastApp == null || lastDecision == null || !lastDecision.approved() || foundCustomer == null || disbursed) {
            return;
        }
        final BusinessCustomer customer = foundCustomer;
        final BusinessApplication app = lastApp;
        final Main.Decision d = lastDecision;
        if (!DisbursementDialog.confirm(this, customer.companyName(), customer.iban(), app.amount())) {
            return;
        }
        final LoanTerms terms = new LoanTerms(
                BigDecimal.valueOf(app.amount()).setScale(2, RoundingMode.HALF_UP),
                app.termYears(), d.rate(), d.monthlyPayment(), d.totalCost(), app.purpose().label);

        disburseBtn.setEnabled(false);
        new SwingWorker<Disbursement, Void>() {
            @Override
            protected Disbursement doInBackground() throws SQLException {
                return AccountDAO.disburseLoan(AccountDAO.TYPE_BUSINESS, customer.id(), terms, Session.currentUser());
            }

            @Override
            protected void done() {
                try {
                    Disbursement result = get();
                    disbursed = true;
                    refreshDisburse();
                    DisbursementDialog.showSuccess(BusinessLoanPanel.this, customer.companyName(), result, app.amount());
                    ContractService.generateAndOpen(BusinessLoanPanel.this, result.loanId());   // σύμβαση PDF
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    disburseBtn.setEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(BusinessLoanPanel.this,
                            "Η εκταμίευση απέτυχε και δεν έγινε καμία πίστωση:\n" + cause.getMessage(),
                            "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showPlaceholder() {
        decisionLabel.setText(" ");
        detailsLabel.setText("<html><i>Συμπληρώστε τη φόρμα και πατήστε «Αξιολόγηση αίτησης».</i></html>");
    }

    private void clearForm() {
        resetAutoFilled();
        statementInfo.setText(" ");
        taxIdField.setText("");
        customerInfo.setText(" ");
        foundCustomer = null;
        lastApp = null;
        lastDecision = null;
        disbursed = false;
        refreshDisburse();
        registryRecord = null;
        showRegistryInfo("<font color='gray'>Πηγή δεδομένων: " + LoanPanel.esc(registry.sourceName()) + "</font>");
        companyName.setText("");
        for (JTextField f : new JTextField[]{yearsInOperation, avgEmployees, totalAssets, netTurnover, equity,
                currentAssets, currentLiabilities, ebitda, netProfit, interestExpense, existingDebtService}) {
            f.setText("0");
        }
        latePayments.setValue(0);
        bouncedCheques.setValue(0);
        arrears.setSelected(false);
        insolvency.setSelected(false);
        amount.setText("");
        term.setValue(7);
        purpose.setSelectedIndex(0);
        updatePreview();
        showPlaceholder();
    }

    /** Ενημερώνει κατηγορία ΕΛΠ και μπάρα βαθμολογίας όσο ο χρήστης πληκτρολογεί. */
    private void updatePreview() {
        // Κατηγορία ΕΛΠ: χρειάζεται μόνο τα τρία κριτήρια μεγέθους
        try {
            EntityCategory c = EntityCategory.classify(
                    LoanPanel.parseNumber(totalAssets, "Σύνολο ενεργητικού", 0),
                    LoanPanel.parseNumber(netTurnover, "Καθαρός κύκλος εργασιών", 0),
                    LoanPanel.parseNumber(avgEmployees, "Μέσος όρος απασχολουμένων", 0));
            categoryLabel.setText(c.label + " οντότητα");
            categoryCriteria.setText("<html>Όρια: " + c.criteriaText() + "</html>");
        } catch (IllegalArgumentException ex) {
            categoryLabel.setText("—");
            categoryCriteria.setText("Ελέγξτε ενεργητικό, κύκλο εργασιών και προσωπικό");
        }

        // Πιστοληπτικός βαθμός
        try {
            BusinessScoreCalculator.BusinessScoreResult sr = BusinessScoreCalculator.calculate(readFinancials());
            CreditTier tier = CreditTier.fromScore(sr.score());
            scoreBar.setValue(sr.score());
            scoreBar.setString(sr.score() + "  –  " + tier.label);
            scoreBar.setForeground(switch (tier) {
                case EXCELLENT, GOOD -> LoanPanel.GREEN;
                case FAIR -> LoanPanel.ORANGE;
                case POOR, REJECTED -> LoanPanel.RED;
            });
            scoreBreakdown.setText(String.format(LoanPanel.GR,
                    "Πληρωμές %.0f | Φερεγγυότητα %.0f | Ρευστότητα %.0f | Κερδοφορία %.0f | Τόκοι %.0f | Ηλικία %.0f",
                    sr.payment(), sr.solvency(), sr.liquidity(), sr.profitability(), sr.interest(), sr.age()));
        } catch (IllegalArgumentException ex) {
            scoreBar.setValue(CreditScoreCalculator.MIN_SCORE);
            scoreBar.setString("Συμπληρώστε τα οικονομικά στοιχεία");
            scoreBreakdown.setText(" ");
        }
    }

    // ===================== ΑΝΑΓΝΩΣΗ ΟΙΚΟΝΟΜΙΚΩΝ ΚΑΤΑΣΤΑΣΕΩΝ =====================

    /** Αντιστοίχιση στοιχείου της κατάστασης -> πεδίο της φόρμας. */
    private record FieldTarget(StatementField field, JTextField component, boolean allowNegative) {}

    private List<FieldTarget> statementTargets() {
        return List.of(
                new FieldTarget(StatementField.TOTAL_ASSETS, totalAssets, false),
                new FieldTarget(StatementField.NET_TURNOVER, netTurnover, false),
                new FieldTarget(StatementField.AVG_EMPLOYEES, avgEmployees, false),
                new FieldTarget(StatementField.EQUITY, equity, true),
                new FieldTarget(StatementField.CURRENT_ASSETS, currentAssets, false),
                new FieldTarget(StatementField.CURRENT_LIABILITIES, currentLiabilities, false),
                new FieldTarget(StatementField.EBITDA, ebitda, true),
                new FieldTarget(StatementField.NET_PROFIT, netProfit, true),
                new FieldTarget(StatementField.INTEREST_EXPENSE, interestExpense, false));
    }

    /** Πατήθηκε το κουμπί «Φόρτωση οικονομικών καταστάσεων». */
    void loadStatement() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Επιλογή οικονομικών καταστάσεων");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Οικονομικές καταστάσεις (PDF, TXT, CSV)", "pdf", "txt", "csv", "tsv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadStatementFile(chooser.getSelectedFile());
        }
    }

    /** Διαβάζει το αρχείο σε δεύτερο νήμα (το PDF μπορεί να αργήσει) και συμπληρώνει τη φόρμα. */
    void loadStatementFile(File file) {
        loadStatementBtn.setEnabled(false);
        showStatementInfo("Ανάγνωση του αρχείου «" + LoanPanel.esc(file.getName())
                + "»... (τα σκαναρισμένα PDF διαβάζονται με OCR και αργούν)");
        new SwingWorker<FinancialStatementParser.ParseResult, Void>() {
            @Override
            protected FinancialStatementParser.ParseResult doInBackground() {
                StatementLoader.Loaded loaded = StatementLoader.read(file);
                if (loaded.text().isBlank()) {
                    throw new StatementException("Δεν βρέθηκε αναγνώσιμο κείμενο στο αρχείο, ούτε με OCR. "
                            + "Συμπληρώστε τα στοιχεία χειροκίνητα.");
                }
                FinancialStatementParser.ParseResult parsed = FinancialStatementParser.parse(loaded.text());
                if (loaded.usedOcr()) {      // το OCR κάνει λάθη στα ψηφία: ο χρήστης πρέπει να ελέγξει τα πάντα
                    List<String> w = new ArrayList<>(parsed.warnings());
                    w.add(0, "ΠΡΟΣΟΧΗ: το PDF είναι σκαναρισμένο και διαβάστηκε με OCR. Ελέγξτε ΟΛΕΣ τις τιμές με το έγγραφο.");
                    parsed = new FinancialStatementParser.ParseResult(parsed.fields(), w);
                }
                return parsed;
            }

            @Override
            protected void done() {
                loadStatementBtn.setEnabled(true);
                try {
                    applyStatementResult(file.getName(), get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showStatementInfo("<font color='" + hex(LoanPanel.RED) + "'>" + LoanPanel.esc(String.valueOf(cause.getMessage())) + "</font>");
                }
            }
        }.execute();
    }

    /** Συμπληρώνει τα πεδία που βρέθηκαν (με πράσινο φόντο) και εμφανίζει τι δεν βρέθηκε και τις προειδοποιήσεις. */
    void applyStatementResult(String fileName, FinancialStatementParser.ParseResult result) {
        // Τιμές που μπήκαν αυτόματα από προηγούμενο έγγραφο δεν πρέπει να μείνουν (θα ανήκαν σε άλλη εταιρεία/χρήση)
        for (JTextField f : autoFilled) f.setText("0");
        resetAutoFilled();
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>(result.warnings());
        int filled = 0;

        for (FieldTarget t : statementTargets()) {
            FinancialStatementParser.Extracted e = result.fields().get(t.field());
            if (e == null) {
                missing.add(t.field().label);
            } else if (e.value() < 0 && !t.allowNegative()) {
                missing.add(t.field().label);
                warnings.add("Η τιμή για «" + t.field().label + "» βγήκε αρνητική και δεν συμπληρώθηκε: ελέγξτε το έγγραφο.");
            } else {
                t.component().setText(toFieldText(e.value()));
                t.component().setBackground(FILLED_BG);
                autoFilled.add(t.component());
                filled++;
            }
        }

        StringBuilder html = new StringBuilder("<b>Οικονομικές καταστάσεις:</b> " + LoanPanel.esc(fileName) + "<br>");
        html.append("Συμπληρώθηκαν <b>").append(filled).append(" από ").append(statementTargets().size())
                .append("</b> πεδία (πράσινο φόντο).");
        if (!missing.isEmpty()) {
            html.append("<br><font color='").append(hex(LoanPanel.ORANGE)).append("'>Δεν βρέθηκαν: ")
                    .append(LoanPanel.esc(String.join(", ", missing))).append(". Συμπληρώστε τα χειροκίνητα.</font>");
        }
        for (String w : warnings) {
            html.append("<br><font color='").append(hex(LoanPanel.RED)).append("'>⚠ ").append(LoanPanel.esc(w)).append("</font>");
        }
        html.append("<br><i>Ελέγξτε τις τιμές πριν την αξιολόγηση. Διαβάστηκε η πρώτη στήλη ποσών (τρέχουσα χρήση). "
                + "Οι δόσεις υφιστάμενων δανείων και τα στοιχεία συμπεριφοράς πληρωμών δεν υπάρχουν στις "
                + "καταστάσεις και συμπληρώνονται χειροκίνητα.</i>");
        showStatementInfo(html.toString());
        updatePreview();
    }

    /** 1234567.5 -> «1234567,5» (το πεδίο ποσού βάζει μόνο του τις τελείες). */
    private static String toFieldText(double v) {
        if (Math.rint(v) == v) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }

    private void resetAutoFilled() {
        for (JTextField f : autoFilled) f.setBackground(defaultFieldBg);
        autoFilled.clear();
    }

    private void showStatementInfo(String htmlBody) {
        statementInfo.setText("<html><div style='width:450px'>" + htmlBody + "</div></html>");
    }

    // ===================== ΔΙΑΣΥΝΔΕΣΗ ΜΕ ΜΗΤΡΩΟ ΑΑΔΕ =====================

    /** Αποτέλεσμα της αναζήτησης: μητρώο ΑΑΔΕ και βάση πελατών της τράπεζας. */
    private record Lookup(Optional<CompanyRecord> registry, String registryError,
                          Optional<BusinessCustomer> customer, boolean belongsToIndividual, String dbError) {}

    /**
     * Πατήθηκε το κουμπί «Αναζήτηση» (ή Enter στο ΑΦΜ). Ψάχνει στην ΑΑΔΕ και στη βάση πελατών.
     * Η κλήση γίνεται σε άλλο νήμα ώστε να μη «παγώνει» η οθόνη.
     */
    void lookupRegistry() {
        String taxId = taxIdField.getText().trim();
        if (!TaxIdValidator.isValid(taxId)) {
            JOptionPane.showMessageDialog(this,
                    "Μη έγκυρος ΑΦΜ. Πρέπει να έχει 9 ψηφία και σωστό ψηφίο ελέγχου.",
                    "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
            return;
        }
        lookupBtn.setEnabled(false);
        showRegistryInfo("Αναζήτηση στην ΑΑΔΕ...");
        showCustomerInfo("Αναζήτηση στη βάση...");
        new SwingWorker<Lookup, Void>() {
            @Override
            protected Lookup doInBackground() {
                Optional<CompanyRecord> record = Optional.empty();
                String registryError = null;
                try {
                    record = registry.findByTaxId(taxId);
                } catch (RuntimeException ex) {
                    registryError = String.valueOf(ex.getMessage());
                }

                Optional<BusinessCustomer> customer = Optional.empty();
                boolean individual = false;
                String dbError = null;
                try {
                    customer = customerDao.findBusinessByAfm(taxId);
                    if (customer.isEmpty()) individual = customerDao.findIndividualByAfm(taxId).isPresent();
                } catch (SQLException ex) {
                    dbError = String.valueOf(ex.getMessage());
                }
                return new Lookup(record, registryError, customer, individual, dbError);
            }

            @Override
            protected void done() {
                lookupBtn.setEnabled(true);
                try {
                    applyLookup(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    registryRecord = null;
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showRegistryInfo("<font color='" + hex(LoanPanel.RED) + "'>Σφάλμα: " + LoanPanel.esc(String.valueOf(cause.getMessage())) + "</font>");
                    customerInfo.setText(" ");
                }
            }
        }.execute();
    }

    private void applyLookup(Lookup r) {
        // 1) Μητρώο ΑΑΔΕ
        if (r.registryError() != null) {
            registryRecord = null;
            showRegistryInfo("<font color='" + hex(LoanPanel.RED) + "'>Σφάλμα ΑΑΔΕ: " + LoanPanel.esc(r.registryError()) + "</font>");
        } else {
            applyRegistryResult(r.registry());
        }

        // 2) Βάση πελατών της τράπεζας: τα στοιχεία της βάσης υπερισχύουν στην επωνυμία
        if (r.customer().isPresent()) {
            BusinessCustomer c = r.customer().get();
            companyName.setText(c.companyName());
            foundCustomer = c;
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(LoanPanel.GREEN) + "'><b>Υπάρχων πελάτης:</b> "
                    + LoanPanel.esc(c.companyName()) + "</font>");
            updatePreview();
            CustomerInfoDialog.showBusiness(this, c);
        } else if (r.dbError() != null) {
            foundCustomer = null;
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(LoanPanel.RED) + "'>Σφάλμα βάσης: " + LoanPanel.esc(r.dbError()) + "</font>");
        } else if (r.belongsToIndividual()) {
            foundCustomer = null;
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(LoanPanel.ORANGE) + "'>Το ΑΦΜ ανήκει σε ιδιώτη. "
                    + "Χρησιμοποιήστε την καρτέλα «Ιδιώτης».</font>");
        } else {
            foundCustomer = null;
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(LoanPanel.RED) + "'>Δεν υπάρχει πελάτης με αυτό το ΑΦΜ στο σύστημα.</font>");
        }
    }

    private void showCustomerInfo(String htmlBody) {
        customerInfo.setText("<html><div style='width:250px'>" + htmlBody + "</div></html>");
    }

    /** Εφαρμόζει το αποτέλεσμα της αναζήτησης στη φόρμα. */
    void applyRegistryResult(Optional<CompanyRecord> result) {
        if (result.isEmpty()) {
            registryRecord = null;
            showRegistryInfo("<font color='" + hex(LoanPanel.RED) + "'>Δεν βρέθηκε ΑΦΜ στην ΑΑΔΕ.</font>");
            return;
        }
        CompanyRecord c = result.get();
        registryRecord = c;

        if (c.legalName() != null) companyName.setText(c.legalName());
        if (c.incorporationDate() != null) {
            yearsInOperation.setText(String.valueOf(c.yearsInOperation(LocalDate.now())));
        }

        String color = hex(c.isActive() ? LoanPanel.GREEN : LoanPanel.RED);
        StringBuilder info = new StringBuilder("<font color='" + color + "'><b>"
                + LoanPanel.esc(c.status() != null ? c.status() : "άγνωστη κατάσταση") + "</b></font>");
        if (c.legalForm() != null) info.append(" – ").append(LoanPanel.esc(c.legalForm()));
        if (c.address() != null) info.append(", ").append(LoanPanel.esc(c.address()));
        if (!c.isActive()) info.append("<br><b>Η επιχείρηση δεν είναι ενεργή: η αίτηση θα απορριφθεί.</b>");
        showRegistryInfo(info.toString());
        updatePreview();
    }

    private void showRegistryInfo(String htmlBody) {
        registryInfo.setText("<html><div style='width:250px'>" + htmlBody + "</div></html>");
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    // ===================== ΒΟΗΘΗΤΙΚΑ =====================

    private BusinessScoreCalculator.BusinessFinancials readFinancials() {
        double none = Double.NEGATIVE_INFINITY;   // επιτρέπονται και αρνητικές τιμές
        return new BusinessScoreCalculator.BusinessFinancials(
                LoanPanel.parseNumber(totalAssets, "Σύνολο ενεργητικού", 0),
                LoanPanel.parseNumber(netTurnover, "Καθαρός κύκλος εργασιών", 0),
                LoanPanel.parseNumber(avgEmployees, "Μέσος όρος απασχολουμένων", 0),
                LoanPanel.parseNumber(equity, "Ίδια κεφάλαια", none),
                LoanPanel.parseNumber(currentAssets, "Κυκλοφορούν ενεργητικό", 0),
                LoanPanel.parseNumber(currentLiabilities, "Βραχυπρόθεσμες υποχρεώσεις", 0),
                LoanPanel.parseNumber(ebitda, "EBITDA", none),
                LoanPanel.parseNumber(netProfit, "Καθαρά αποτελέσματα χρήσης", none),
                LoanPanel.parseNumber(interestExpense, "Χρεωστικοί τόκοι", 0),
                LoanPanel.parseNumber(existingDebtService, "Ετήσιες δόσεις υφιστάμενων δανείων", 0),
                LoanPanel.parseNumber(yearsInOperation, "Έτη λειτουργίας", 0),
                LoanPanel.intValue(latePayments), LoanPanel.intValue(bouncedCheques),
                arrears.isSelected(), insolvency.isSelected());
    }
}
