package bank.account;

import LoanApp.LoanApp.TaxIdValidator;
import bank.auth.Session;
import bank.registration.BusinessCustomer;
import bank.registration.CustomerDAO;
import bank.registration.IbanGenerator;
import bank.registration.IndividualCustomer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Λογαριασμοί πελατών (ιδιωτών και επιχειρήσεων): κατάθεση, ανάληψη, μεταφορά σε άλλον λογαριασμό,
 * ιστορικό κινήσεων, πληρωμή δόσεων δανείων και σύμβαση PDF.
 */
public class AccountPanel extends JPanel {

    private static final Locale GR = Locale.forLanguageTag("el-GR");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color RED = new Color(0xC62828);

    /** Ο πελάτης που έχει επιλεγεί (ιδιώτης ή επιχείρηση). */
    private record Holder(int type, int id, String name, String afm, String iban, BigDecimal balance) {
        Holder withBalance(BigDecimal b) { return new Holder(type, id, name, afm, iban, b); }
    }

    private final CustomerDAO customerDao = new CustomerDAO();

    private final JTextField afmField = new JTextField(10);
    private final JButton searchBtn = new JButton("Αναζήτηση");
    private final JLabel searchInfo = new JLabel(" ");

    private final JLabel nameLabel = new JLabel("—");
    private final JLabel typeLabel = new JLabel("—");
    private final JLabel ibanLabel = new JLabel("—");
    private final JLabel accountNoLabel = new JLabel("—");
    private final JLabel balanceLabel = new JLabel("—");

    private final JTextField amountField = new LoanApp.LoanApp.MoneyField("", false);
    private final JTextField destIbanField = new bank.ui.PlaceholderField(26, "IBAN ή 10ψήφιος αριθμός λογαριασμού");
    private final JButton depositBtn = new JButton("Κατάθεση");
    private final JButton withdrawBtn = new JButton("Ανάληψη");
    private final JButton transferBtn = new JButton("Μεταφορά");

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"Ημερομηνία", "Κίνηση", "Ποσό", "Υπόλοιπο μετά", "Περιγραφή"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel loansModel = new DefaultTableModel(
            new String[]{"Αρ.", "Ημερομηνία", "Ποσό", "Σκοπός", "Μηνιαία δόση", "Δόσεις", "Υπόλοιπο οφειλής", "Κατάσταση"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable loansTable = new JTable(loansModel);
    private final JButton payBtn = new JButton("Πληρωμή δόσης");
    private final JButton contractBtn = new JButton("Σύμβαση PDF");
    private final List<LoanInfo> loans = new ArrayList<>();

    private Holder holder;

    public AccountPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Λογαριασμοί πελατών", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(10));
        top.add(buildSearchPanel());
        top.add(Box.createVerticalStrut(8));
        top.add(buildInfoPanel());
        top.add(Box.createVerticalStrut(8));
        top.add(buildOperationPanel());
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Κινήσεις", buildHistoryPanel());
        tabs.addTab("Δάνεια", buildLoansPanel());
        add(tabs, BorderLayout.CENTER);

        setOperationsEnabled(false);
        setPreferredSize(new Dimension(860, 760));
    }

    // ------------------------------------------------------------------ UI

    private JPanel buildSearchPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setBorder(BorderFactory.createTitledBorder("Πελάτης"));
        p.add(new JLabel("ΑΦΜ:"));
        p.add(afmField);
        p.add(searchBtn);
        p.add(searchInfo);
        searchBtn.addActionListener(e -> search());
        afmField.addActionListener(e -> search());
        return p;
    }

    private JPanel buildInfoPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Στοιχεία λογαριασμού"));
        balanceLabel.setFont(balanceLabel.getFont().deriveFont(Font.BOLD, 22f));
        addInfoRow(p, 0, "Δικαιούχος:", nameLabel);
        addInfoRow(p, 1, "Τύπος:", typeLabel);
        addInfoRow(p, 2, "IBAN:", ibanLabel);
        addInfoRow(p, 3, "Αρ. λογαριασμού:", accountNoLabel);
        addInfoRow(p, 4, "Υπόλοιπο:", balanceLabel);
        return p;
    }

    private JPanel buildOperationPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Κίνηση"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0; p.add(new JLabel("Ποσό (€):"), c);
        c.gridx = 1; p.add(amountField, c);
        c.gridx = 2; p.add(depositBtn, c);
        c.gridx = 3; p.add(withdrawBtn, c);

        c.gridy = 1;
        c.gridx = 0; p.add(new JLabel("IBAN ή αρ. λογαριασμού προορισμού:"), c);
        c.gridx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; p.add(destIbanField, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        c.gridx = 3; p.add(transferBtn, c);

        depositBtn.addActionListener(e -> execute(AccountDAO.TX_DEPOSIT));
        withdrawBtn.addActionListener(e -> execute(AccountDAO.TX_WITHDRAWAL));
        transferBtn.addActionListener(e -> transfer());
        return p;
    }

    private JScrollPane buildHistoryPanel() {
        JTable table = new JTable(historyModel);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(right);
        table.getColumnModel().getColumn(3).setCellRenderer(right);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(320);
        return new JScrollPane(table);
    }

    private JPanel buildLoansPanel() {
        loansTable.setRowHeight(22);
        loansTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loansTable.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int col : new int[]{2, 4, 6}) {
            loansTable.getColumnModel().getColumn(col).setCellRenderer(right);
        }
        loansTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        loansTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        payBtn.addActionListener(e -> payInstallment());
        contractBtn.addActionListener(e -> openContract());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        buttons.add(payBtn);
        buttons.add(contractBtn);
        buttons.add(new JLabel("(επίλεξε πρώτα ένα δάνειο από τον πίνακα)"));

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(loansTable), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private static void addInfoRow(JPanel p, int row, String label, JLabel value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(Color.GRAY);
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(value, c);
    }

    private void setOperationsEnabled(boolean enabled) {
        amountField.setEnabled(enabled);
        destIbanField.setEnabled(enabled);
        depositBtn.setEnabled(enabled);
        withdrawBtn.setEnabled(enabled);
        transferBtn.setEnabled(enabled);
        payBtn.setEnabled(enabled);
        contractBtn.setEnabled(enabled);
    }

    // ------------------------------------------------------------------ Αναζήτηση

    private void search() {
        String afm = afmField.getText().trim();
        if (!TaxIdValidator.isValid(afm)) {
            JOptionPane.showMessageDialog(this,
                    "Μη έγκυρος ΑΦΜ. Πρέπει να έχει 9 ψηφία και σωστό ψηφίο ελέγχου.",
                    "Μη έγκυρα στοιχεία", JOptionPane.WARNING_MESSAGE);
            return;
        }
        searchBtn.setEnabled(false);
        searchInfo.setForeground(Color.GRAY);
        searchInfo.setText("Αναζήτηση...");
        new SwingWorker<Holder, Void>() {
            @Override
            protected Holder doInBackground() throws SQLException {
                Optional<IndividualCustomer> ind = customerDao.findIndividualByAfm(afm);
                if (ind.isPresent()) {
                    IndividualCustomer c = ind.get();
                    return new Holder(AccountDAO.TYPE_INDIVIDUAL, c.id(), c.fullName(), c.afm(), c.iban(), c.balance());
                }
                Optional<BusinessCustomer> biz = customerDao.findBusinessByAfm(afm);
                if (biz.isPresent()) {
                    BusinessCustomer c = biz.get();
                    return new Holder(AccountDAO.TYPE_BUSINESS, c.id(), c.companyName(), c.afm(), c.iban(), c.balance());
                }
                return null;
            }

            @Override
            protected void done() {
                searchBtn.setEnabled(true);
                try {
                    Holder h = get();
                    if (h == null) {
                        clearHolder();
                        searchInfo.setForeground(RED);
                        searchInfo.setText("Δεν υπάρχει πελάτης με αυτό το ΑΦΜ.");
                    } else {
                        searchInfo.setText(" ");
                        showHolder(h);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    clearHolder();
                    searchInfo.setForeground(RED);
                    searchInfo.setText("Σφάλμα βάσης: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void showHolder(Holder h) {
        holder = h;
        nameLabel.setText(h.name());
        typeLabel.setText(h.type() == AccountDAO.TYPE_INDIVIDUAL ? "Ιδιώτης" : "Επιχείρηση");
        ibanLabel.setText(h.iban() == null || h.iban().isBlank()
                ? "(θα δημιουργηθεί στην πρώτη κίνηση)" : IbanGenerator.format(h.iban().trim()));
        accountNoLabel.setText(IbanGenerator.accountNumber(h.iban()));
        balanceLabel.setText(money(h.balance()));
        setOperationsEnabled(true);
        amountField.setText("");
        destIbanField.setText("");
        loadHistory();
        loadLoans();
    }

    private void clearHolder() {
        holder = null;
        nameLabel.setText("—");
        typeLabel.setText("—");
        ibanLabel.setText("—");
        accountNoLabel.setText("—");
        balanceLabel.setText("—");
        historyModel.setRowCount(0);
        loansModel.setRowCount(0);
        loans.clear();
        setOperationsEnabled(false);
    }

    private void applyNewBalance(BigDecimal newBalance) {
        if (holder != null) {
            holder = holder.withBalance(newBalance);
            balanceLabel.setText(money(newBalance));
        }
    }

    // ------------------------------------------------------------------ Κατάθεση / Ανάληψη

    private void execute(String txType) {
        if (holder == null) return;
        BigDecimal amount = parseAmount(amountField.getText());
        if (amount == null) {
            showInvalidAmount();
            return;
        }
        boolean deposit = AccountDAO.TX_DEPOSIT.equals(txType);
        if (!deposit && holder.balance() != null && amount.compareTo(holder.balance()) > 0) {
            JOptionPane.showMessageDialog(this, "Ανεπαρκές υπόλοιπο. Διαθέσιμο: " + money(holder.balance()),
                    "Ανάληψη", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String note = askJustificationIfLarge(amount, deposit ? "Προέλευση κεφαλαίων" : "Αιτιολογία ανάληψης");
        if (note == null) return;     // ακυρώθηκε

        String verb = deposit ? "κατάθεση" : "ανάληψη";
        int ok = JOptionPane.showConfirmDialog(this,
                "Να γίνει " + verb + " " + money(amount) + "\nΔικαιούχος: " + holder.name(),
                "Επιβεβαίωση", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        final Holder h = holder;
        final String finalNote = note;
        setOperationsEnabled(false);
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws SQLException {
                return AccountDAO.applyTransaction(h.type(), h.id(), txType, amount, finalNote, Session.currentUser());
            }

            @Override
            protected void done() {
                try {
                    BigDecimal newBalance = get();
                    if (sameHolder(h)) {
                        applyNewBalance(newBalance);
                        amountField.setText("");
                        loadHistory();
                    }
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            (deposit ? "Η κατάθεση" : "Η ανάληψη") + " ολοκληρώθηκε.\nΝέο υπόλοιπο: " + money(newBalance),
                            "Επιτυχία", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showFailure("Η κίνηση δεν ολοκληρώθηκε", ex);
                } finally {
                    setOperationsEnabled(holder != null);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ Μεταφορά

    private void transfer() {
        if (holder == null) return;
        BigDecimal amount = parseAmount(amountField.getText());
        if (amount == null) {
            showInvalidAmount();
            return;
        }
        String input = IbanGenerator.cleanInput(destIbanField.getText());
        if (!IbanGenerator.isAccountNumber(input) && !IbanGenerator.isValid(input)) {
            JOptionPane.showMessageDialog(this,
                    "Μη έγκυρος προορισμός. Γράψε είτε ολόκληρο το IBAN (27 χαρακτήρες, ξεκινά από GR) "
                    + "είτε τον αριθμό λογαριασμού (τα 10 τελευταία ψηφία του IBAN).",
                    "Μεταφορά", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (holder.balance() != null && amount.compareTo(holder.balance()) > 0) {
            JOptionPane.showMessageDialog(this, "Ανεπαρκές υπόλοιπο. Διαθέσιμο: " + money(holder.balance()),
                    "Μεταφορά", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Εύρεση του λογαριασμού προορισμού (σε άλλο νήμα) και μετά συνέχεια της μεταφοράς
        final Holder src = holder;
        setOperationsEnabled(false);
        new SwingWorker<List<AccountDAO.AccountMatch>, Void>() {
            @Override protected List<AccountDAO.AccountMatch> doInBackground() throws SQLException {
                return AccountDAO.findByIbanOrAccountNumber(input);
            }
            @Override protected void done() {
                try {
                    continueTransfer(src, amount, get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showFailure("Η αναζήτηση του λογαριασμού προορισμού απέτυχε", ex);
                } finally {
                    setOperationsEnabled(holder != null);
                }
            }
        }.execute();
    }

    private void continueTransfer(Holder src, BigDecimal amount, List<AccountDAO.AccountMatch> found) {
        if (!sameHolder(src)) return;                       // ο χρήστης άλλαξε πελάτη στο μεταξύ
        String ownIban = src.iban() == null ? "" : src.iban().trim();
        List<AccountDAO.AccountMatch> matches = new ArrayList<>();
        for (AccountDAO.AccountMatch m : found) {
            if (!m.iban().equals(ownIban)) matches.add(m);
        }
        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, found.isEmpty()
                            ? "Ο προορισμός δεν ανήκει σε λογαριασμό της τράπεζας."
                            : "Ο προορισμός είναι ο ίδιος λογαριασμός.",
                    "Μεταφορά", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AccountDAO.AccountMatch dest = matches.get(0);
        if (matches.size() > 1) {
            // Ο ίδιος αριθμός λογαριασμού σε ιδιώτη και επιχείρηση: ο υπάλληλος διαλέγει τον σωστό
            String[] options = new String[matches.size()];
            for (int i = 0; i < options.length; i++) {
                AccountDAO.AccountMatch m = matches.get(i);
                options[i] = m.typeLabel() + " – " + m.name() + " (ΑΦΜ " + m.afm() + ")";
            }
            Object choice = JOptionPane.showInputDialog(this,
                    "Ο αριθμός λογαριασμού αντιστοιχεί σε περισσότερους από έναν λογαριασμούς.\n"
                    + "Διάλεξε τον δικαιούχο (ή γράψε ολόκληρο το IBAN):",
                    "Μεταφορά", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == null) return;
            dest = matches.get(java.util.Arrays.asList(options).indexOf(choice));
        }
        final String destIban = dest.iban();

        String note = askJustificationIfLarge(amount, "Αιτιολογία μεταφοράς");
        if (note == null) return;

        int ok = JOptionPane.showConfirmDialog(this,
                "Να μεταφερθούν " + money(amount) + "\nΑπό: " + src.name()
                + "\nΠρος: " + dest.name()
                + "\nIBAN: " + IbanGenerator.format(destIban)
                + "\nΑρ. λογαριασμού: " + IbanGenerator.accountNumber(destIban),
                "Επιβεβαίωση μεταφοράς", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        final Holder h = src;
        final String finalNote = note;
        setOperationsEnabled(false);
        new SwingWorker<TransferResult, Void>() {
            @Override
            protected TransferResult doInBackground() throws SQLException {
                return AccountDAO.transfer(h.type(), h.id(), destIban, amount, finalNote, Session.currentUser());
            }

            @Override
            protected void done() {
                try {
                    TransferResult r = get();
                    if (sameHolder(h)) {
                        applyNewBalance(r.newBalance());
                        amountField.setText("");
                        destIbanField.setText("");
                        loadHistory();
                    }
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Η μεταφορά ολοκληρώθηκε.\nΔικαιούχος: " + r.destName()
                            + "\nΝέο υπόλοιπο: " + money(r.newBalance()),
                            "Επιτυχία", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showFailure("Η μεταφορά δεν ολοκληρώθηκε", ex);
                } finally {
                    setOperationsEnabled(holder != null);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ Δάνεια

    private void payInstallment() {
        LoanInfo loan = selectedLoan();
        if (loan == null) return;
        if (!loan.isActive()) {
            JOptionPane.showMessageDialog(this, "Το δάνειο #" + loan.id() + " έχει ήδη εξοφληθεί.",
                    "Δόση δανείου", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        BigDecimal due = loan.nextInstallment();
        if (holder.balance() != null && due.compareTo(holder.balance()) > 0) {
            JOptionPane.showMessageDialog(this,
                    "Ανεπαρκές υπόλοιπο για τη δόση (" + money(due) + "). Διαθέσιμο: " + money(holder.balance()),
                    "Δόση δανείου", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int number = loan.installmentsPaid() + 1;
        int ok = JOptionPane.showConfirmDialog(this,
                "Πληρωμή δόσης " + number + "/" + loan.months() + " του δανείου #" + loan.id()
                + "\nΠοσό: " + money(due) + "\nΘα χρεωθεί ο λογαριασμός του πελάτη.",
                "Επιβεβαίωση", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        final Holder h = holder;
        setOperationsEnabled(false);
        new SwingWorker<PaymentResult, Void>() {
            @Override
            protected PaymentResult doInBackground() throws SQLException {
                return LoanDAO.payInstallment(loan.id(), Session.currentUser());
            }

            @Override
            protected void done() {
                try {
                    PaymentResult r = get();
                    if (sameHolder(h)) {
                        applyNewBalance(r.newBalance());
                        loadHistory();
                        loadLoans();
                    }
                    JOptionPane.showMessageDialog(AccountPanel.this,
                            "Πληρώθηκε η δόση " + r.installmentNumber() + "/" + r.totalInstallments() + ": " + money(r.paid())
                            + (r.loanClosed() ? "\nΤο δάνειο εξοφλήθηκε πλήρως." : "")
                            + "\nΝέο υπόλοιπο λογαριασμού: " + money(r.newBalance()),
                            "Επιτυχία", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showFailure("Η δόση δεν πληρώθηκε", ex);
                } finally {
                    setOperationsEnabled(holder != null);
                }
            }
        }.execute();
    }

    private void openContract() {
        LoanInfo loan = selectedLoan();
        if (loan != null) {
            ContractService.generateAndOpen(this, loan.id());
        }
    }

    private LoanInfo selectedLoan() {
        int row = loansTable.getSelectedRow();
        if (row < 0 || row >= loans.size()) {
            JOptionPane.showMessageDialog(this, "Επίλεξε πρώτα ένα δάνειο από τον πίνακα.",
                    "Δάνεια", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return loans.get(row);
    }

    // ------------------------------------------------------------------ Φόρτωση πινάκων

    private void loadHistory() {
        final Holder h = holder;
        new SwingWorker<List<TransactionRecord>, Void>() {
            @Override
            protected List<TransactionRecord> doInBackground() throws SQLException {
                return AccountDAO.recentTransactions(h.iban(), 30);
            }

            @Override
            protected void done() {
                try {
                    List<TransactionRecord> rows = get();
                    if (!sameHolder(h)) return;
                    historyModel.setRowCount(0);
                    for (TransactionRecord t : rows) {
                        historyModel.addRow(new Object[]{
                                t.date().format(DATE_TIME), typeName(t.type()),
                                signedMoney(t.amount()), money(t.balanceAfter()),
                                t.description() == null ? "" : t.description()});
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    historyModel.setRowCount(0);
                }
            }
        }.execute();
    }

    private void loadLoans() {
        final Holder h = holder;
        new SwingWorker<List<LoanInfo>, Void>() {
            @Override
            protected List<LoanInfo> doInBackground() throws SQLException {
                return LoanDAO.loansOf(h.type(), h.id());
            }

            @Override
            protected void done() {
                try {
                    List<LoanInfo> list = get();
                    if (!sameHolder(h)) return;
                    loans.clear();
                    loans.addAll(list);
                    loansModel.setRowCount(0);
                    for (LoanInfo l : list) {
                        loansModel.addRow(new Object[]{
                                l.id(), l.date().format(DATE_ONLY), money(l.amount()), l.purpose(),
                                money(l.monthlyPayment()), l.installmentsPaid() + "/" + l.months(),
                                money(l.remaining()), l.isActive() ? "Ενεργό" : "Εξοφλημένο"});
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    loansModel.setRowCount(0);
                    loans.clear();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ Βοηθητικά

    private boolean sameHolder(Holder h) {
        return holder != null && holder.id() == h.id() && holder.type() == h.type();
    }

    /**
     * Για μεγάλα ποσά ζητά αιτιολογία. Επιστρέφει "" αν δεν χρειάζεται, το κείμενο αν δόθηκε,
     * ή null αν ο χρήστης ακύρωσε.
     */
    private String askJustificationIfLarge(BigDecimal amount, String prompt) {
        if (amount.compareTo(Limits.LARGE_TRANSACTION_THRESHOLD) < 0) {
            return "";
        }
        while (true) {
            String text = JOptionPane.showInputDialog(this,
                    "Το ποσό είναι " + money(amount) + " (όριο ελέγχου: " + money(Limits.LARGE_TRANSACTION_THRESHOLD)
                    + ").\n" + prompt + " (υποχρεωτικό):",
                    "Μεγάλο ποσό", JOptionPane.QUESTION_MESSAGE);
            if (text == null) return null;
            if (!text.isBlank()) return text.trim();
            JOptionPane.showMessageDialog(this, "Η αιτιολογία είναι υποχρεωτική για αυτό το ποσό.",
                    "Μεγάλο ποσό", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showInvalidAmount() {
        JOptionPane.showMessageDialog(this,
                "Δώσε έγκυρο θετικό ποσό (έως 2 δεκαδικά ψηφία), π.χ. 150 ή 150,50.",
                "Μη έγκυρο ποσό", JOptionPane.WARNING_MESSAGE);
    }

    private void showFailure(String title, ExecutionException ex) {
        JOptionPane.showMessageDialog(this, title + ":\n" + rootMessage(ex), "Σφάλμα", JOptionPane.ERROR_MESSAGE);
    }

    /** Δέχεται 150, 150.5, 150,50 ή 1.250,75. Επιστρέφει null αν το ποσό δεν είναι έγκυρο. */
    private static BigDecimal parseAmount(String text) {
        if (text == null) return null;
        String t = text.trim().replace(" ", "").replace("€", "");
        if (t.isEmpty()) return null;
        t = LoanApp.LoanApp.MoneyField.toPlain(t);
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.signum() <= 0 || v.scale() > 2 || v.compareTo(MAX_AMOUNT) > 0) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String typeName(String type) {
        return switch (type == null ? "" : type) {
            case AccountDAO.TX_DEPOSIT -> "Κατάθεση";
            case AccountDAO.TX_WITHDRAWAL -> "Ανάληψη";
            case "TRANSFER_OUT" -> "Μεταφορά (εκροή)";
            case "TRANSFER_IN" -> "Μεταφορά (εισροή)";
            case "LOAN_DISBURSEMENT" -> "Εκταμίευση δανείου";
            case "LOAN_PAYMENT" -> "Δόση δανείου";
            default -> String.valueOf(type);
        };
    }

    private static String money(BigDecimal v) {
        return String.format(GR, "%,.2f €", v == null ? BigDecimal.ZERO : v);
    }

    private static String signedMoney(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + money(v);
    }

    private static String rootMessage(ExecutionException ex) {
        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
        return String.valueOf(c.getMessage());
    }
}
