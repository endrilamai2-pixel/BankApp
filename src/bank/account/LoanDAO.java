package bank.account;

import bank.ledger.LedgerService;
import bank.registration.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Δάνεια πελατών: λίστα, πληρωμή δόσης και δεδομένα σύμβασης. */
public final class LoanDAO {

    private LoanDAO() { }

    public static List<LoanInfo> loansOf(int customerType, int customerId) throws SQLException {
        String sql = "SELECT id, created_at, amount, purpose, term_years, monthly_payment, total_cost, "
                   + "installments_paid, paid_total, status FROM loans "
                   + "WHERE customer_type = ? AND customer_id = ? ORDER BY id DESC";
        List<LoanInfo> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerType);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LoanInfo(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(), rs.getBigDecimal(3),
                            rs.getString(4), rs.getInt(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                            rs.getInt(8), rs.getBigDecimal(9), rs.getString(10)));
                }
            }
        }
        return list;
    }

    /**
     * Πληρώνει την επόμενη δόση του δανείου με χρέωση του λογαριασμού του πελάτη.
     * Ο έλεγχος υπολοίπου, η χρέωση, η κίνηση και η ενημέρωση του δανείου γίνονται σε μία συναλλαγή.
     */
    public static PaymentResult payInstallment(long loanId, String performedBy) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                int type, customerId, termYears, paidCount;
                BigDecimal monthly, total, paidTotal, loanAmount, loanRate;
                String status;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT customer_type, customer_id, term_years, monthly_payment, total_cost, "
                      + "installments_paid, paid_total, status, amount, interest_rate FROM loans WITH (UPDLOCK, ROWLOCK) WHERE id = ?")) {
                    ps.setLong(1, loanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Το δάνειο δεν βρέθηκε.");
                        }
                        type = rs.getInt(1);
                        customerId = rs.getInt(2);
                        termYears = rs.getInt(3);
                        monthly = rs.getBigDecimal(4);
                        total = rs.getBigDecimal(5);
                        paidCount = rs.getInt(6);
                        paidTotal = rs.getBigDecimal(7);
                        status = rs.getString(8);
                        loanAmount = rs.getBigDecimal(9);
                        loanRate = rs.getBigDecimal(10);
                    }
                }
                BigDecimal remaining = total.subtract(paidTotal);
                if ("PAID".equals(status == null ? "" : status.trim()) || remaining.signum() <= 0) {
                    throw new SQLException("Το δάνειο έχει ήδη εξοφληθεί.");
                }
                int months = termYears * 12;
                boolean last = paidCount + 1 >= months;
                BigDecimal due = last ? remaining : monthly.min(remaining);

                String table = type == AccountDAO.TYPE_INDIVIDUAL ? "individual_customers" : "business_customers";
                String iban = AccountDAO.lockAndGetIban(c, table, type, customerId);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
                    ps.setBigDecimal(1, due);
                    ps.setInt(2, customerId);
                    ps.setBigDecimal(3, due);
                    if (ps.executeUpdate() == 0) {
                        throw new AccountDAO.InsufficientFundsException("Ανεπαρκές υπόλοιπο για την πληρωμή της δόσης.");
                    }
                }
                BigDecimal newBalance = AccountDAO.readBalance(c, table, customerId);

                int number = paidCount + 1;
                AccountDAO.insertTransaction(c, iban, "LOAN_PAYMENT", due.negate(), newBalance,
                        "Δόση δανείου #" + loanId + " (" + number + "/" + months + ")", loanId, performedBy);

                BigDecimal[] split = splitInstallment(loanAmount, loanRate, months, monthly, number, due);
                LedgerService.postInstallment(c, type, loanId, split[0], split[1], performedBy);

                BigDecimal newPaid = paidTotal.add(due);
                boolean closed = last || newPaid.compareTo(total) >= 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE loans SET installments_paid = ?, paid_total = ?, status = ? WHERE id = ?")) {
                    ps.setInt(1, number);
                    ps.setBigDecimal(2, newPaid);
                    ps.setString(3, closed ? "PAID" : "ACTIVE");
                    ps.setLong(4, loanId);
                    ps.executeUpdate();
                }
                c.commit();
                return new PaymentResult(due, newBalance, number, months, closed);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Χωρίζει μια δόση σε {κεφάλαιο, τόκος} με βάση τον πίνακα αποπληρωμής. Το άθροισμα είναι πάντα ίσο με το ποσό
     * που πληρώθηκε (due), ώστε η λογιστική εγγραφή να ισοσκελίζει.
     */
    static BigDecimal[] splitInstallment(BigDecimal amount, BigDecimal ratePercent, int months, BigDecimal monthly,
                                         int number, BigDecimal due) {
        List<AmortizationSchedule.Row> rows = AmortizationSchedule.build(amount, ratePercent, months, monthly);
        BigDecimal principal = rows.get(Math.min(number, rows.size()) - 1).principal().min(due).max(BigDecimal.ZERO);
        return new BigDecimal[]{principal, due.subtract(principal)};
    }

    /** Το κεφάλαιο που απομένει να εξοφληθεί στα ενεργά δάνεια ενός τύπου πελάτη (για συμφωνία με το καθολικό). */
    public static BigDecimal outstandingPrincipal(Connection c, int customerType) throws SQLException {
        String sql = "SELECT amount, term_years, interest_rate, monthly_payment, installments_paid FROM loans "
                   + "WHERE customer_type = ? AND status <> 'PAID'";
        BigDecimal sum = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal amount = rs.getBigDecimal(1);
                    int months = rs.getInt(2) * 12;
                    int paid = rs.getInt(5);
                    if (paid <= 0) {
                        sum = sum.add(amount);
                    } else if (paid < months) {
                        List<AmortizationSchedule.Row> rows =
                                AmortizationSchedule.build(amount, rs.getBigDecimal(3), months, rs.getBigDecimal(4));
                        sum = sum.add(rows.get(paid - 1).balance());
                    }
                }
            }
        }
        return sum;
    }

    /** Συγκεντρώνει από τη βάση όλα τα στοιχεία για τη σύμβαση ενός δανείου. */
    public static ContractData loadContractData(long loanId) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            int type, customerId, termYears;
            String iban, purpose, approvedBy;
            BigDecimal amount, rate, monthly, total;
            java.time.LocalDateTime date;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT customer_type, customer_id, iban, amount, term_years, interest_rate, monthly_payment, "
                  + "total_cost, purpose, approved_by, created_at FROM loans WHERE id = ?")) {
                ps.setLong(1, loanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Το δάνειο #" + loanId + " δεν βρέθηκε.");
                    }
                    type = rs.getInt(1);
                    customerId = rs.getInt(2);
                    iban = rs.getString(3).trim();
                    amount = rs.getBigDecimal(4);
                    termYears = rs.getInt(5);
                    rate = rs.getBigDecimal(6);
                    monthly = rs.getBigDecimal(7);
                    total = rs.getBigDecimal(8);
                    purpose = rs.getString(9);
                    approvedBy = rs.getString(10);
                    date = rs.getTimestamp(11).toLocalDateTime();
                }
            }

            String name, afm, idNumber = null, profession, address;
            if (type == AccountDAO.TYPE_INDIVIDUAL) {
                String sql = "SELECT first_name, last_name, id_number, afm, profession, home_address "
                           + "FROM individual_customers WHERE id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Ο πελάτης του δανείου δεν βρέθηκε.");
                        name = rs.getString(1) + " " + rs.getString(2);
                        idNumber = rs.getString(3);
                        afm = rs.getString(4);
                        profession = rs.getString(5);
                        address = rs.getString(6);
                    }
                }
            } else {
                String sql = "SELECT company_name, afm, activity, headquarters FROM business_customers WHERE id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Η επιχείρηση του δανείου δεν βρέθηκε.");
                        name = rs.getString(1);
                        afm = rs.getString(2);
                        profession = rs.getString(3);
                        address = rs.getString(4);
                    }
                }
            }
            return new ContractData(loanId, date, type, name, afm, idNumber, profession, address, iban,
                    amount, termYears, rate, monthly, total, purpose, approvedBy);
        }
    }
}
