package bank.account;

import bank.auth.Session;
import bank.ledger.AccountType;
import bank.ledger.ChartOfAccounts;
import bank.ledger.GlAccount;
import bank.ledger.LedgerDAO;
import bank.ledger.LedgerDAO.AccountBalance;
import bank.ledger.LedgerDAO.JournalLine;
import bank.ledger.LedgerService;
import bank.registration.DatabaseManager;
import bank.ui.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Λογιστική της τράπεζας: ισολογισμός, ισοζύγιο και ημερολόγιο εγγραφών. */
public class LedgerPanel extends JPanel {

    private static final Locale GR = Locale.forLanguageTag("el-GR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Όλα όσα φορτώνονται από τη βάση σε κάθε ανανέωση. */
    private record Data(List<AccountBalance> balances, List<JournalLine> journal, int entryCount,
                        BigDecimal customerDepositsInd, BigDecimal customerDepositsBiz,
                        BigDecimal loansOutstandingInd, BigDecimal loansOutstandingBiz) { }

    private final DefaultTableModel assetsModel = model("Ενεργητικό", "Ποσό (€)");
    private final DefaultTableModel liabilitiesModel = model("Παθητικό και ίδια κεφάλαια", "Ποσό (€)");
    private final DefaultTableModel trialModel = model("Κωδικός", "Λογαριασμός", "Χρέωση (€)", "Πίστωση (€)");
    private final DefaultTableModel journalModel = model("Εγγραφή", "Ημερομηνία", "Περιγραφή", "Λογαριασμός", "Χρέωση (€)", "Πίστωση (€)");
    private final JLabel checkLabel = new JLabel(" ");
    private final JLabel reconcileLabel = new JLabel(" ");
    private volatile boolean loading = false;
    private final JButton refreshButton = new JButton("Ανανέωση");
    private final JButton capitalButton = new JButton("Εισφορά κεφαλαίου…");
    private final JButton openingButton = new JButton("Αρχικά υπόλοιπα…");

    public LedgerPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Λογιστική – Ενεργητικό της τράπεζας", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel note = new JLabel("Εκπαιδευτικό σχέδιο λογαριασμών – δεν είναι το επίσημο ΚΛΣΤ", SwingConstants.CENTER);
        note.setForeground(Theme.MUTED);
        JPanel north = new JPanel(new GridLayout(2, 1));
        north.add(title);
        north.add(note);
        add(north, BorderLayout.NORTH);

        JPanel sheet = new JPanel(new BorderLayout(0, 8));
        JPanel tables = new JPanel(new GridLayout(2, 1, 0, 8));
        tables.add(new JScrollPane(statementTable(assetsModel)));
        tables.add(new JScrollPane(statementTable(liabilitiesModel)));
        JPanel status = new JPanel(new GridLayout(2, 1));
        status.add(checkLabel);
        status.add(reconcileLabel);
        sheet.add(tables, BorderLayout.CENTER);
        sheet.add(status, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ισολογισμός", sheet);
        tabs.addTab("Ισοζύγιο", new JScrollPane(numericTable(trialModel, 2, 3)));
        tabs.addTab("Ημερολόγιο", new JScrollPane(numericTable(journalModel, 4, 5)));
        add(tabs, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> refresh());
        capitalButton.addActionListener(e -> onShareCapital());
        openingButton.addActionListener(e -> onOpeningBalances());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        buttons.add(refreshButton);
        buttons.add(capitalButton);
        buttons.add(openingButton);
        add(buttons, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(780, 640));
        refresh();

        // Αυτόματη ανανέωση όσο το παράθυρο είναι ανοιχτό, ώστε να φαίνονται οι νέες κινήσεις χωρίς κουμπί
        Timer auto = new Timer(5000, e -> {
            if (isShowing() && !loading) refresh();
        });
        auto.setInitialDelay(5000);
        auto.start();
    }

    // ------------------------------------------------------------------ φόρτωση

    private void refresh() {
        loading = true;
        refreshButton.setEnabled(false);
        new SwingWorker<Data, Void>() {
            @Override protected Data doInBackground() throws Exception { return load(); }
            @Override protected void done() {
                refreshButton.setEnabled(true);
                loading = false;
                try {
                    show(get());
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(LedgerPanel.this, "Αδυναμία φόρτωσης του καθολικού:\n" + t.getMessage(),
                            "Σφάλμα", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static Data load() throws SQLException {
        List<AccountBalance> balances = LedgerDAO.balances();
        List<JournalLine> journal = LedgerDAO.journal(500);
        int count = LedgerDAO.entryCount();
        try (Connection c = DatabaseManager.getConnection()) {
            return new Data(balances, journal, count,
                    sumBalances(c, "individual_customers"), sumBalances(c, "business_customers"),
                    LoanDAO.outstandingPrincipal(c, ChartOfAccounts.CUSTOMER_INDIVIDUAL),
                    LoanDAO.outstandingPrincipal(c, ChartOfAccounts.CUSTOMER_BUSINESS));
        }
    }

    private static BigDecimal sumBalances(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(balance), 0) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    // ------------------------------------------------------------------ εμφάνιση

    private void show(Data d) {
        Map<String, AccountBalance> byCode = new TreeMap<>();
        for (AccountBalance b : d.balances()) byCode.put(b.account().code(), b);

        BigDecimal assets = fillSection(assetsModel, byCode, AccountType.ASSET);
        BigDecimal liabilities = fillSection(liabilitiesModel, byCode, AccountType.LIABILITY, AccountType.EQUITY);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (AccountBalance b : byCode.values()) {
            if (!b.account().isLeaf()) continue;
            if (b.account().type() == AccountType.INCOME) income = income.add(b.net());
            if (b.account().type() == AccountType.EXPENSE) expense = expense.add(b.net());
        }
        BigDecimal result = income.subtract(expense);
        liabilitiesModel.addRow(new Object[]{"Αποτέλεσμα χρήσης (έσοδα − έξοδα)", fmt(result)});
        liabilities = liabilities.add(result);
        assetsModel.addRow(new Object[]{"ΣΥΝΟΛΟ ΕΝΕΡΓΗΤΙΚΟΥ", fmt(assets)});
        liabilitiesModel.addRow(new Object[]{"ΣΥΝΟΛΟ ΠΑΘΗΤΙΚΟΥ ΚΑΙ ΙΔΙΩΝ ΚΕΦΑΛΑΙΩΝ", fmt(liabilities)});

        BigDecimal diff = assets.subtract(liabilities);
        BigDecimal cash = byCode.containsKey(ChartOfAccounts.CASH) ? byCode.get(ChartOfAccounts.CASH).net() : BigDecimal.ZERO;
        if (diff.signum() != 0) {
            checkLabel.setForeground(new Color(0xC62828));
            checkLabel.setText("Ο ισολογισμός ΔΕΝ ισοσκελίζει (διαφορά " + fmt(diff) + " €).");
        } else if (cash.signum() < 0) {
            checkLabel.setForeground(new Color(0xB26A00));
            checkLabel.setText("Ισοσκελίζει, αλλά το ταμείο είναι αρνητικό: κατάγραψε πρώτα εισφορά κεφαλαίου.");
        } else {
            checkLabel.setForeground(new Color(0x1A7F37));
            checkLabel.setText("Ο ισολογισμός ισοσκελίζει.");
        }

        // Συμφωνία καθολικού με τα υπόλοιπα των πελατών
        BigDecimal glDeposits = netOf(byCode, ChartOfAccounts.DEPOSITS_INDIVIDUALS).add(netOf(byCode, ChartOfAccounts.DEPOSITS_BUSINESSES));
        BigDecimal subDeposits = d.customerDepositsInd().add(d.customerDepositsBiz());
        BigDecimal glLoans = netOf(byCode, ChartOfAccounts.LOANS_INDIVIDUALS).add(netOf(byCode, ChartOfAccounts.LOANS_BUSINESSES));
        BigDecimal subLoans = d.loansOutstandingInd().add(d.loansOutstandingBiz());
        boolean ok = glDeposits.compareTo(subDeposits) == 0 && glLoans.compareTo(subLoans) == 0;
        reconcileLabel.setForeground(ok ? new Color(0x1A7F37) : new Color(0xB26A00));
        reconcileLabel.setText((ok ? "Συμφωνία με λογαριασμούς πελατών: ΟΚ" : "Διαφορά από τους λογαριασμούς πελατών")
                + "  |  καταθέσεις " + fmt(glDeposits) + " / " + fmt(subDeposits)
                + "  |  χορηγήσεις " + fmt(glLoans) + " / " + fmt(subLoans));

        openingButton.setEnabled(d.entryCount() == 0);
        openingButton.putClientProperty("data", d);

        // Ισοζύγιο
        trialModel.setRowCount(0);
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (AccountBalance b : byCode.values()) {
            if (!b.account().isLeaf()) continue;
            trialModel.addRow(new Object[]{b.account().code(), b.account().name(), fmt(b.debit()), fmt(b.credit())});
            totalDebit = totalDebit.add(b.debit());
            totalCredit = totalCredit.add(b.credit());
        }
        trialModel.addRow(new Object[]{"", "ΣΥΝΟΛΑ", fmt(totalDebit), fmt(totalCredit)});

        // Ημερολόγιο
        journalModel.setRowCount(0);
        long lastEntry = -1;
        for (JournalLine l : d.journal()) {
            boolean first = l.entryId() != lastEntry;
            lastEntry = l.entryId();
            journalModel.addRow(new Object[]{
                    first ? "#" + l.entryId() : "", first ? l.date().format(DATE) : "", first ? l.description() : "",
                    l.accountCode() + " " + l.accountName(),
                    l.debit().signum() == 0 ? "" : fmt(l.debit()), l.credit().signum() == 0 ? "" : fmt(l.credit())});
        }
    }

    /** Γράφει ένα τμήμα του ισολογισμού (πρωτοβάθμιοι με υπολογαριασμούς από κάτω) και επιστρέφει το σύνολό του. */
    private BigDecimal fillSection(DefaultTableModel model, Map<String, AccountBalance> byCode, AccountType... types) {
        model.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        for (AccountBalance head : byCode.values()) {
            GlAccount h = head.account();
            if (h.isLeaf() || !contains(types, h.type())) continue;
            List<AccountBalance> children = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (AccountBalance b : byCode.values()) {
                if (h.code().equals(b.account().parentCode())) {
                    children.add(b);
                    sum = sum.add(b.net());
                }
            }
            model.addRow(new Object[]{h.code() + "  " + h.name(), fmt(sum)});
            for (AccountBalance b : children) {
                model.addRow(new Object[]{"      " + b.account().code() + "  " + b.account().name(), fmt(b.net())});
            }
            total = total.add(sum);
        }
        return total;
    }

    private static boolean contains(AccountType[] types, AccountType t) {
        for (AccountType x : types) if (x == t) return true;
        return false;
    }

    private static BigDecimal netOf(Map<String, AccountBalance> byCode, String code) {
        AccountBalance b = byCode.get(code);
        return b == null ? BigDecimal.ZERO : b.net();
    }

    // ------------------------------------------------------------------ ενέργειες

    private void onShareCapital() {
        String input = JOptionPane.showInputDialog(this, "Ποσό εισφοράς μετοχικού κεφαλαίου σε μετρητά (€):",
                "Εισφορά κεφαλαίου", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        try {
            BigDecimal amount = new BigDecimal(input.trim().replace(" ", "").replace(",", "."));
            LedgerService.postShareCapital(amount, Session.currentUser());
            refresh();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Μη έγκυρο ποσό.", "Σφάλμα", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Σφάλμα βάσης δεδομένων:\n" + ex.getMessage(), "Σφάλμα", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onOpeningBalances() {
        Object o = openingButton.getClientProperty("data");
        if (!(o instanceof Data d)) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Θα καταχωρηθεί μία εγγραφή με τα υπάρχοντα υπόλοιπα πελατών και τα ανεξόφλητα δάνεια\n"
              + "(καταθέσεις " + fmt(d.customerDepositsInd().add(d.customerDepositsBiz())) + " €, χορηγήσεις "
              + fmt(d.loansOutstandingInd().add(d.loansOutstandingBiz())) + " €).\nΝα συνεχίσω;",
                "Αρχικά υπόλοιπα", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            LedgerService.postOpeningBalances(d.loansOutstandingInd(), d.loansOutstandingBiz(),
                    d.customerDepositsInd(), d.customerDepositsBiz(), Session.currentUser());
            refresh();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Πληροφορία", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Σφάλμα βάσης δεδομένων:\n" + ex.getMessage(), "Σφάλμα", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ βοηθητικά UI

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    private static JTable statementTable(DefaultTableModel m) {
        return numericTable(m, 1);
    }

    private static JTable numericTable(DefaultTableModel m, int... rightAlignedColumns) {
        JTable t = new JTable(m);
        Theme.styleTable(t, rightAlignedColumns);
        return t;
    }

    private static String fmt(BigDecimal v) {
        return String.format(GR, "%,.2f", v);
    }
}
