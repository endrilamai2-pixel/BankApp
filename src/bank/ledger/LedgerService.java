package bank.ledger;

import bank.ledger.LedgerDAO.Line;
import bank.registration.DatabaseManager;
import bank.registration.IbanGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Κανόνες λογιστικής αποτύπωσης των πράξεων της τράπεζας. Οι μέθοδοι που παίρνουν Connection δεν κάνουν commit:
 * τρέχουν μέσα στη συναλλαγή της πράξης.
 *
 * Απλοποίηση: οι τόκοι αναγνωρίζονται ως έσοδο όταν εισπράττονται (ταμειακή βάση), χωρίς δουλευμένους τόκους.
 */
public final class LedgerService {
    private LedgerService() { }

    /** Εκταμίευση: η τράπεζα αποκτά απαίτηση (χορήγηση) και δημιουργεί υποχρέωση (κατάθεση) προς τον πελάτη. */
    public static void postDisbursement(Connection c, int customerType, long loanId, BigDecimal amount,
                                        String user) throws SQLException {
        LedgerDAO.post(c, "Εκταμίευση δανείου #" + loanId, "LOAN_DISBURSEMENT", loanId, user, List.of(
                Line.debit(ChartOfAccounts.loansOf(customerType), amount),
                Line.credit(ChartOfAccounts.depositsOf(customerType), amount)));
    }

    /** Κατάθεση: αυξάνεται το ταμείο και η υποχρέωση προς τον πελάτη. Ανάληψη: το αντίστροφο. */
    public static void postCashMovement(Connection c, int customerType, boolean deposit, BigDecimal amount,
                                        String customerName, String iban, String note,
                                        String user) throws SQLException {
        String deposits = ChartOfAccounts.depositsOf(customerType);
        List<Line> lines = deposit
                ? List.of(Line.debit(ChartOfAccounts.CASH, amount), Line.credit(deposits, amount))
                : List.of(Line.debit(deposits, amount), Line.credit(ChartOfAccounts.CASH, amount));
        String description = (deposit ? "Κατάθεση" : "Ανάληψη") + " – " + customerName
                + " (" + IbanGenerator.format(iban.trim()) + ")"
                + (note == null || note.isBlank() ? "" : " – " + note.trim());
        if (description.length() > 250) description = description.substring(0, 250);
        LedgerDAO.post(c, description, deposit ? "DEPOSIT" : "WITHDRAWAL", null, user, lines);
    }

    /**
     * Πληρωμή δόσης: μειώνονται οι καταθέσεις του πελάτη κατά το ποσό της δόσης, μειώνεται η χορήγηση κατά το κεφάλαιο
     * και αναγνωρίζεται έσοδο για τον τόκο.
     */
    public static void postInstallment(Connection c, int customerType, long loanId, BigDecimal principal,
                                       BigDecimal interest, String user) throws SQLException {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.debit(ChartOfAccounts.depositsOf(customerType), principal.add(interest)));
        if (principal.signum() > 0) lines.add(Line.credit(ChartOfAccounts.loansOf(customerType), principal));
        if (interest.signum() > 0) lines.add(Line.credit(ChartOfAccounts.INTEREST_INCOME, interest));
        LedgerDAO.post(c, "Δόση δανείου #" + loanId, "LOAN_PAYMENT", loanId, user, lines);
    }

    /**
     * Μεταφορά μεταξύ πελατών. Αν είναι ίδιου τύπου, ο λογαριασμός «Καταθέσεις» δεν αλλάζει συνολικά και δεν γίνεται
     * εγγραφή (η κίνηση φαίνεται μόνο στους λογαριασμούς των πελατών). Αν είναι ιδιώτης → επιχείρηση ή αντίστροφα,
     * μετακινείται υπόλοιπο ανάμεσα στους δύο λογαριασμούς καταθέσεων.
     */
    public static void postTransfer(Connection c, int srcType, int dstType, BigDecimal amount,
                                    String user) throws SQLException {
        if (srcType == dstType) return;
        LedgerDAO.post(c, "Μεταφορά μεταξύ διαφορετικών τύπων πελατών", "TRANSFER", null, user, List.of(
                Line.debit(ChartOfAccounts.depositsOf(srcType), amount),
                Line.credit(ChartOfAccounts.depositsOf(dstType), amount)));
    }

    /**
     * Επένδυση: τα μετρητά μετατρέπονται σε χρεόγραφα/μετοχές στην αξία κτήσης. Το συνολικό ενεργητικό δεν αλλάζει.
     * Δεν κάνει commit και δεν ελέγχει υπόλοιπο μετρητών (ο έλεγχος γίνεται από τον καλούντα στην ίδια συναλλαγή).
     */
    public static void postInvestment(Connection c, String securitiesAccount, BigDecimal amount,
                                      String label, String user) throws SQLException {
        LedgerDAO.post(c, "Επένδυση σε: " + label, "INVESTMENT", null, user, List.of(
                Line.debit(securitiesAccount, amount),
                Line.credit(ChartOfAccounts.CASH, amount)));
    }

    /** Εισφορά μετοχικού κεφαλαίου σε μετρητά (δική της συναλλαγή). */
    public static void postShareCapital(BigDecimal amount, String user) throws SQLException {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        if (a.signum() <= 0) throw new IllegalArgumentException("Το ποσό πρέπει να είναι θετικό.");
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                LedgerDAO.post(c, "Εισφορά μετοχικού κεφαλαίου", "SHARE_CAPITAL", null, user, List.of(
                        Line.debit(ChartOfAccounts.CASH, a),
                        Line.credit(ChartOfAccounts.SHARE_CAPITAL, a)));
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Αρχικές εγγραφές από δεδομένα που υπήρχαν πριν την εισαγωγή του καθολικού. Η διαφορά ανάμεσα σε χορηγήσεις και
     * καταθέσεις πηγαίνει στο λογαριασμό «Διαφορά αρχικών υπολοίπων», ώστε η εγγραφή να ισοσκελίζει.
     */
    public static void postOpeningBalances(BigDecimal loansInd, BigDecimal loansBiz,
                                           BigDecimal depositsInd, BigDecimal depositsBiz,
                                           String user) throws SQLException {
        List<Line> lines = new ArrayList<>();
        addIfPositive(lines, true, ChartOfAccounts.LOANS_INDIVIDUALS, loansInd);
        addIfPositive(lines, true, ChartOfAccounts.LOANS_BUSINESSES, loansBiz);
        addIfPositive(lines, false, ChartOfAccounts.DEPOSITS_INDIVIDUALS, depositsInd);
        addIfPositive(lines, false, ChartOfAccounts.DEPOSITS_BUSINESSES, depositsBiz);
        BigDecimal diff = loansInd.add(loansBiz).subtract(depositsInd).add(depositsBiz.negate());   // χρεώσεις - πιστώσεις
        if (diff.signum() > 0) addIfPositive(lines, false, ChartOfAccounts.OPENING_DIFFERENCE, diff);
        if (diff.signum() < 0) addIfPositive(lines, true, ChartOfAccounts.OPENING_DIFFERENCE, diff.negate());
        if (lines.size() < 2) throw new IllegalArgumentException("Δεν υπάρχουν υπάρχοντα υπόλοιπα για μεταφορά στο καθολικό.");
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                LedgerDAO.post(c, "Αρχικά υπόλοιπα από υπάρχοντα δεδομένα", "OPENING", null, user, lines);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void addIfPositive(List<Line> lines, boolean debit, String account, BigDecimal amount) {
        if (amount.signum() <= 0) return;
        lines.add(debit ? Line.debit(account, amount) : Line.credit(account, amount));
    }
}
