package LoanApp.LoanApp;
import bank.account.AccountDAO;
import bank.account.ContractService;
import bank.account.Disbursement;
import bank.account.DisbursementDialog;
import bank.account.LoanTerms;
import bank.auth.Session;
import bank.registration.CustomerDAO;
import bank.registration.CustomerInfoDialog;
import bank.registration.IndividualCustomer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.text.ParseException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Η κύρια οθόνη της εφαρμογής: φόρμα αριστερά, αποτέλεσμα δεξιά.
 * Το εκτιμώμενο credit score ενημερώνεται ζωντανά καθώς συμπληρώνονται τα στοιχεία.
 */
public class LoanPanel extends JPanel {

    static final Locale GR = Locale.forLanguageTag("el-GR");
    static final Color GREEN = new Color(0x1E8E3E);
    static final Color RED = new Color(0xC62828);
    static final Color ORANGE = new Color(0xE65100);

    // ---------- Πεδία φόρμας ----------
    final JTextField afmField = new JTextField(10);
    final JButton lookupBtn = new JButton("Αναζήτηση");
    final JLabel customerInfo = new JLabel(" ");
    final CustomerDAO customerDao = new CustomerDAO();

    // ---------- Εκταμίευση ----------
    final JButton disburseBtn = new JButton("Εκταμίευση δανείου στον λογαριασμό του πελάτη");
    final JLabel disburseNote = new JLabel(" ");
    IndividualCustomer foundCustomer;      // ο πελάτης της τελευταίας επιτυχούς αναζήτησης (ΑΦΜ)
    Main.Application lastApp;              // η τελευταία αξιολόγηση
    Main.Decision lastDecision;
    boolean disbursed;                     // έχει ήδη εκταμιευτεί η τελευταία αξιολόγηση
    final JTextField firstNameField = readOnlyField();
    final JTextField lastNameField = readOnlyField();
    final JTextField professionField = readOnlyField();
    final JTextField idNumberField = readOnlyField();
    final JTextField dateOfBirthField = readOnlyField();
    final JTextField phoneField = readOnlyField();

    final JSpinner late30 = intSpinner(0, 100, 0);
    final JSpinner late60 = intSpinner(0, 100, 0);
    final JSpinner late90 = intSpinner(0, 100, 0);
    final JCheckBox bankrupt = new JCheckBox("Ναι");
    final JTextField creditLimit = new MoneyField("0", false);
    final JTextField creditUsed = new MoneyField("0", false);
    final JTextField historyYears = new JTextField("0");
    final JSpinner inquiries = intSpinner(0, 100, 0);
    final JSpinner accountTypes = intSpinner(0, 10, 0);

    final JTextField assets = new MoneyField("", false);
    final JTextField amount = new MoneyField("", false);
    final JSpinner term = intSpinner(1, LoanAppParameters.MAX_TERM_YEARS, 10);
    final JComboBox<Purpose> purpose = new JComboBox<>(Purpose.values());

    // ---------- Πεδία αποτελέσματος ----------
    final JProgressBar scoreBar = new JProgressBar(CreditScoreCalculator.MIN_SCORE, CreditScoreCalculator.MAX_SCORE);
    final JLabel scoreBreakdown = new JLabel(" ");
    final JLabel decisionLabel = new JLabel(" ", SwingConstants.CENTER);
    final JLabel detailsLabel = new JLabel();

    public LoanPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        purpose.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Purpose p) setText(p.label);
                return this;
            }
        });

        JScrollPane formScroll = new JScrollPane(buildForm());
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(formScroll, BorderLayout.WEST);
        add(buildResultPanel(), BorderLayout.CENTER);

        // Ζωντανή ενημέρωση του credit score
        for (JSpinner s : new JSpinner[]{late30, late60, late90, inquiries, accountTypes}) {
            s.addChangeListener(e -> updateScorePreview());
        }
        bankrupt.addActionListener(e -> updateScorePreview());
        // Αν αλλάξει το ΑΦΜ μετά την αναζήτηση, ο πελάτης δεν θεωρείται πλέον επιβεβαιωμένος
        onTextChange(afmField, () -> {
            if (foundCustomer != null && !foundCustomer.afm().equals(afmField.getText().trim())) {
                foundCustomer = null;
                clearPersonalDetails();
                refreshDisburse();
            }
        });
        for (JTextField f : new JTextField[]{creditLimit, creditUsed, historyYears}) {
            onTextChange(f, this::updateScorePreview);
        }
        updateScorePreview();
        showPlaceholder();
    }

    // ===================== ΚΑΤΑΣΚΕΥΗ ΕΜΦΑΝΙΣΗΣ =====================

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel personal = section("Στοιχεία αιτούντος");
        lookupBtn.addActionListener(e -> lookupCustomer());
        afmField.addActionListener(e -> lookupCustomer());      // Enter μέσα στο πεδίο ΑΦΜ
        JPanel afmPanel = new JPanel(new BorderLayout(6, 0));
        afmPanel.add(afmField, BorderLayout.CENTER);
        afmPanel.add(lookupBtn, BorderLayout.EAST);
        addRow(personal, 0, "ΑΦΜ", afmPanel);
        addRow(personal, 1, "", customerInfo);
        addRow(personal, 2, "Όνομα", firstNameField);
        addRow(personal, 3, "Επώνυμο", lastNameField);
        addRow(personal, 4, "Επάγγελμα", professionField);
        addRow(personal, 5, "Αρ. Ταυτότητας", idNumberField);
        addRow(personal, 6, "Ημερομηνία Γέννησης", dateOfBirthField);
        addRow(personal, 7, "Τηλέφωνο", phoneField);

        JPanel history = section("Πιστοληπτικό ιστορικό");
        addRow(history, 0, "Καθυστερήσεις 30+ ημερών", late30);
        addRow(history, 1, "Καθυστερήσεις 60+ ημερών", late60);
        addRow(history, 2, "Καθυστερήσεις 90+ ημερών", late90);
        addRow(history, 3, "Πτώχευση / αθέτηση", bankrupt);
        addRow(history, 4, "Συνολικό όριο καρτών (€)", creditLimit);
        addRow(history, 5, "Χρησιμοποιημένο υπόλοιπο (€)", creditUsed);
        addRow(history, 6, "Έτη πιστωτικού ιστορικού", historyYears);
        addRow(history, 7, "Αιτήσεις πίστωσης (12 μήνες)", inquiries);
        addRow(history, 8, "Είδη πίστωσης (κάρτα, στεγαστικό...)", accountTypes);

        JPanel loan = section("Στοιχεία δανείου");
        addRow(loan, 0, "Περιουσιακά στοιχεία (€)", assets);
        addRow(loan, 1, "Ποσό δανείου (€)", amount);
        addRow(loan, 2, "Διάρκεια (έτη)", term);
        addRow(loan, 3, "Σκοπός δανείου", purpose);

        JButton evaluateBtn = new JButton("Αξιολόγηση αίτησης");
        evaluateBtn.setFont(evaluateBtn.getFont().deriveFont(Font.BOLD, 14f));
        evaluateBtn.addActionListener(e -> evaluate());
        JButton clearBtn = new JButton("Καθαρισμός");
        clearBtn.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(clearBtn);
        buttons.add(evaluateBtn);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (JPanel p : new JPanel[]{personal, history, loan}) {
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(p);
            form.add(Box.createVerticalStrut(8));
        }
        form.add(buttons);
        getRootPaneSafe(evaluateBtn);
        return form;
    }

    private void getRootPaneSafe(JButton defaultButton) {
        // Το Enter ενεργοποιεί το κουμπί αξιολόγησης μόλις το panel μπει σε παράθυρο
        addHierarchyListener(e -> {
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null && isShowing()) root.setDefaultButton(defaultButton);
        });
    }

    private JPanel buildResultPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Αποτέλεσμα"));

        JLabel scoreTitle = new JLabel("Εκτιμώμενο credit score (ζωντανά)");
        scoreTitle.setFont(scoreTitle.getFont().deriveFont(Font.BOLD));
        scoreTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        scoreBar.setStringPainted(true);
        scoreBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoreBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        scoreBreakdown.setAlignmentX(Component.LEFT_ALIGNMENT);

        decisionLabel.setFont(decisionLabel.getFont().deriveFont(Font.BOLD, 26f));
        decisionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        decisionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        decisionLabel.setHorizontalAlignment(SwingConstants.LEFT);

        detailsLabel.setVerticalAlignment(SwingConstants.TOP);
        detailsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(Box.createVerticalStrut(6));
        panel.add(scoreTitle);
        panel.add(Box.createVerticalStrut(6));
        panel.add(scoreBar);
        panel.add(Box.createVerticalStrut(4));
        panel.add(scoreBreakdown);
        panel.add(Box.createVerticalStrut(16));
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(16));
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
        panel.setPreferredSize(new Dimension(460, 520));
        return panel;
    }

    static JPanel section(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(title), new EmptyBorder(2, 6, 6, 6)));
        return p;
    }

    static void addRow(JPanel p, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.weightx = 0;
        p.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }

    static JSpinner intSpinner(int min, int max, int value) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
    }

    // ===================== ΕΝΕΡΓΕΙΕΣ =====================

    /** Πατήθηκε το κουμπί «Αξιολόγηση αίτησης». */
    void evaluate() {
        try {
            String name = (firstNameField.getText().trim() + " " + lastNameField.getText().trim()).trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Συμπληρώστε το ονοματεπώνυμο.");

            CreditScoreCalculator.ScoreResult sr = CreditScoreCalculator.calculate(readProfile());
            double assetsValue = parseNumber(assets, "Περιουσιακά στοιχεία", 0);
            double amountValue = parseNumber(amount, "Ποσό δανείου", 1);
            int termValue = intValue(term);
            Purpose p = (Purpose) purpose.getSelectedItem();

            Main.Application app = new Main.Application(
                    name, sr.score(), assetsValue, amountValue, termValue, p);
            Main.Decision d = Calculations.evaluate(app);
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

    private void showResult(Main.Application app, CreditScoreCalculator.ScoreResult sr, Main.Decision d) {
        StringBuilder html = new StringBuilder("<html><table cellpadding='3'>");
        row(html, "Αιτών", esc(app.name()));
        row(html, "Σκοπός", app.purpose().label);
        row(html, "Ιστορικό", d.tier().label + " (" + sr.score() + ")");
        row(html, "Ποσό", money(app.amount()));
        row(html, "Διάρκεια", app.termYears() + " έτη");

        if (d.approved()) {
            decisionLabel.setText("✔ ΕΓΚΡΙΣΗ");
            decisionLabel.setForeground(GREEN);
            row(html, "Επιτόκιο", String.format(GR, "%.2f%%", d.rate()));
            row(html, "Μηνιαία δόση", "<b>" + money(d.monthlyPayment()) + "</b>");
            row(html, "Συνολικό κόστος", money(d.totalCost()));
            row(html, "Συνολικοί τόκοι", money(d.totalCost() - app.amount()));
        } else {
            decisionLabel.setText("✘ ΑΠΟΡΡΙΨΗ");
            decisionLabel.setForeground(RED);
            row(html, "Λόγος", "<div style='width:260px'>" + esc(d.reason()) + "</div>");
        }
        html.append("</table></html>");
        detailsLabel.setText(html.toString());
    }

    // ===================== ΑΝΑΖΗΤΗΣΗ ΠΕΛΑΤΗ ΣΤΗ ΒΑΣΗ =====================

    /** Αποτέλεσμα αναζήτησης: ο πελάτης ιδιώτης, ή ένδειξη ότι το ΑΦΜ ανήκει σε επιχείρηση. */
    private record CustomerLookup(Optional<IndividualCustomer> customer, boolean belongsToBusiness) {}

    /** Πατήθηκε «Αναζήτηση» (ή Enter στο ΑΦΜ). Η βάση ερωτάται σε άλλο νήμα, ώστε να μη «παγώνει» η οθόνη. */
    void lookupCustomer() {
        String afm = afmField.getText().trim();
        if (!TaxIdValidator.isValid(afm)) {
            JOptionPane.showMessageDialog(this,
                    "Μη έγκυρος ΑΦΜ. Πρέπει να έχει 9 ψηφία και σωστό ψηφίο ελέγχου.",
                    "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
            return;
        }
        lookupBtn.setEnabled(false);
        showCustomerInfo("Αναζήτηση στη βάση...");
        new SwingWorker<CustomerLookup, Void>() {
            @Override
            protected CustomerLookup doInBackground() throws SQLException {
                Optional<IndividualCustomer> c = customerDao.findIndividualByAfm(afm);
                boolean isBusiness = c.isEmpty() && customerDao.findBusinessByAfm(afm).isPresent();
                return new CustomerLookup(c, isBusiness);
            }

            @Override
            protected void done() {
                lookupBtn.setEnabled(true);
                try {
                    applyCustomerResult(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showCustomerInfo("<font color='" + hex(RED) + "'>Σφάλμα βάσης: "
                            + esc(String.valueOf(cause.getMessage())) + "</font>");
                }
            }
        }.execute();
    }

    private void applyCustomerResult(CustomerLookup result) {
        if (result.customer().isPresent()) {
            IndividualCustomer c = result.customer().get();
            firstNameField.setText(c.firstName());
            lastNameField.setText(c.lastName());
            professionField.setText(c.profession());
            idNumberField.setText(c.idNumber());
            dateOfBirthField.setText(c.formattedDateOfBirth());
            phoneField.setText(c.phone() == null ? "" : c.phone());
            foundCustomer = c;
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(GREEN) + "'><b>Υπάρχων πελάτης:</b> "
                    + esc(c.fullName()) + "</font>");
            CustomerInfoDialog.showIndividual(this, c);
        } else if (result.belongsToBusiness()) {
            foundCustomer = null;
            clearPersonalDetails();
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(ORANGE) + "'>Το ΑΦΜ ανήκει σε επιχείρηση. "
                    + "Χρησιμοποιήστε την καρτέλα «Επιχείρηση».</font>");
        } else {
            foundCustomer = null;
            clearPersonalDetails();
            refreshDisburse();
            showCustomerInfo("<font color='" + hex(RED) + "'>Δεν υπάρχει πελάτης με αυτό το ΑΦΜ στο σύστημα.</font>");
        }
    }

    private void showCustomerInfo(String htmlBody) {
        customerInfo.setText("<html><div style='width:250px'>" + htmlBody + "</div></html>");
    }

    static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    // ===================== ΕΚΤΑΜΙΕΥΣΗ ΔΑΝΕΙΟΥ =====================

    /** Το κουμπί εκταμίευσης εμφανίζεται μόνο για εγκεκριμένη αίτηση υπάρχοντος (επιβεβαιωμένου με ΑΦΜ) πελάτη. */
    private void refreshDisburse() {
        boolean approved = lastDecision != null && lastDecision.approved();
        boolean eligible = approved && !disbursed && foundCustomer != null
                && foundCustomer.afm().equals(afmField.getText().trim());
        disburseBtn.setVisible(eligible);
        disburseBtn.setEnabled(eligible);
        if (disbursed) {
            disburseNote.setText("<html><font color='" + hex(GREEN) + "'><b>Το δάνειο εκταμιεύθηκε και πιστώθηκε στον λογαριασμό.</b></font></html>");
        } else if (approved && !eligible) {
            disburseNote.setText("<html><font color='" + hex(ORANGE) + "'>Για να πιστωθεί το δάνειο, κάνε πρώτα αναζήτηση "
                    + "υπάρχοντος πελάτη με το ΑΦΜ του.</font></html>");
        } else {
            disburseNote.setText(" ");
        }
    }

    /** Πατήθηκε «Εκταμίευση»: πιστώνει το ποσό του εγκεκριμένου δανείου στον λογαριασμό του πελάτη. */
    void disburse() {
        if (lastApp == null || lastDecision == null || !lastDecision.approved() || foundCustomer == null || disbursed) {
            return;
        }
        final IndividualCustomer customer = foundCustomer;
        final Main.Application app = lastApp;
        final Main.Decision d = lastDecision;
        if (!DisbursementDialog.confirm(this, customer.fullName(), customer.iban(), app.amount())) {
            return;
        }
        final LoanTerms terms = new LoanTerms(
                BigDecimal.valueOf(app.amount()).setScale(2, RoundingMode.HALF_UP),
                app.termYears(), d.rate(), d.monthlyPayment(), d.totalCost(), app.purpose().label);

        disburseBtn.setEnabled(false);
        new SwingWorker<Disbursement, Void>() {
            @Override
            protected Disbursement doInBackground() throws SQLException {
                return AccountDAO.disburseLoan(AccountDAO.TYPE_INDIVIDUAL, customer.id(), terms, Session.currentUser());
            }

            @Override
            protected void done() {
                try {
                    Disbursement result = get();
                    disbursed = true;
                    refreshDisburse();
                    DisbursementDialog.showSuccess(LoanPanel.this, customer.fullName(), result, app.amount());
                    ContractService.generateAndOpen(LoanPanel.this, result.loanId());   // σύμβαση PDF
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    disburseBtn.setEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LoanPanel.this,
                            "Η εκταμίευση απέτυχε και δεν έγινε καμία πίστωση:\n" + cause.getMessage(),
                            "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static JTextField readOnlyField() {
        JTextField field = new JTextField(16);
        field.setEditable(false);
        field.setFocusable(false);
        return field;
    }

    private void clearPersonalDetails() {
        for (JTextField field : new JTextField[]{firstNameField, lastNameField, professionField,
                idNumberField, dateOfBirthField, phoneField}) field.setText("");
    }

    private void showPlaceholder() {
        decisionLabel.setText(" ");
        detailsLabel.setText("<html><i>Συμπληρώστε τη φόρμα και πατήστε «Αξιολόγηση αίτησης».</i></html>");
    }

    private void clearForm() {
        afmField.setText("");
        customerInfo.setText(" ");
        foundCustomer = null;
        lastApp = null;
        lastDecision = null;
        disbursed = false;
        refreshDisburse();
        clearPersonalDetails();
        for (JSpinner s : new JSpinner[]{late30, late60, late90, inquiries, accountTypes}) s.setValue(0);
        bankrupt.setSelected(false);
        creditLimit.setText("0");
        creditUsed.setText("0");
        historyYears.setText("0");
        assets.setText("");
        amount.setText("");
        term.setValue(10);
        purpose.setSelectedIndex(0);
        updateScorePreview();
        showPlaceholder();
    }

    /** Ενημερώνει τη μπάρα credit score όσο ο χρήστης πληκτρολογεί. */
    private void updateScorePreview() {
        try {
            CreditScoreCalculator.ScoreResult sr = CreditScoreCalculator.calculate(readProfile());
            CreditTier tier = CreditTier.fromScore(sr.score());
            scoreBar.setValue(sr.score());
            scoreBar.setString(sr.score() + "  –  " + tier.label);
            scoreBar.setForeground(switch (tier) {
                case EXCELLENT, GOOD -> GREEN;
                case FAIR -> ORANGE;
                case POOR, REJECTED -> RED;
            });
            scoreBreakdown.setText(String.format(GR,
                    "Πληρωμές %.0f  |  Χρήση πίστωσης %.0f  |  Διάρκεια %.0f  |  Αιτήματα %.0f  |  Ποικιλία %.0f",
                    sr.paymentHistory(), sr.utilization(), sr.historyLength(), sr.inquiries(), sr.creditMix()));
        } catch (IllegalArgumentException ex) {
            scoreBar.setValue(CreditScoreCalculator.MIN_SCORE);
            scoreBar.setString("Ελέγξτε τα πεδία του ιστορικού");
            scoreBreakdown.setText(" ");
        }
    }

    // ===================== ΒΟΗΘΗΤΙΚΑ =====================

    private CreditScoreCalculator.CreditProfile readProfile() {
        return new CreditScoreCalculator.CreditProfile(
                intValue(late30), intValue(late60), intValue(late90), bankrupt.isSelected(),
                parseNumber(creditLimit, "Συνολικό όριο καρτών", 0),
                parseNumber(creditUsed, "Χρησιμοποιημένο υπόλοιπο", 0),
                parseNumber(historyYears, "Έτη πιστωτικού ιστορικού", 0),
                intValue(inquiries), intValue(accountTypes));
    }

    static int intValue(JSpinner s) {
        try {
            s.commitEdit();               // πάρε και ό,τι έχει πληκτρολογηθεί αλλά δεν επιβεβαιώθηκε
        } catch (ParseException ignored) {
        }
        return ((Number) s.getValue()).intValue();
    }

    static double parseNumber(JTextField f, String fieldName, double min) {
        // Στα πεδία ποσού οι τελείες είναι διαχωριστικά χιλιάδων και το κόμμα είναι υποδιαστολή
        String text = (f instanceof MoneyField)
                ? MoneyField.toPlain(f.getText())
                : f.getText().trim().replace(',', '.');
        double v;
        try {
            v = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Μη έγκυρος αριθμός στο πεδίο «" + fieldName + "».");
        }
        if (v < min) {
            throw new IllegalArgumentException("Το πεδίο «" + fieldName + "» πρέπει να είναι τουλάχιστον " + min + ".");
        }
        return v;
    }

    static void onTextChange(JTextField f, Runnable action) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); }
            public void removeUpdate(DocumentEvent e) { action.run(); }
            public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td><font color='gray'>").append(label).append("</font></td><td>")
                .append(value).append("</td></tr>");
    }

    static String money(double v) {
        return String.format(GR, "%,.2f €", v);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

