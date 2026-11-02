package bank.account;

import bank.ledger.LedgerDAO;
import bank.ledger.LedgerService;
import bank.registration.DatabaseManager;
import bank.registration.IbanGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Υπόλοιπα λογαριασμών, δάνεια και κινήσεις. */
public final class AccountDAO {

    public static final int TYPE_INDIVIDUAL = IbanGenerator.TYPE_INDIVIDUAL;
    public static final int TYPE_BUSINESS = IbanGenerator.TYPE_BUSINESS;

    private AccountDAO() { }

    /** Προσθέτει τη στήλη υπολοίπου στους πίνακες πελατών και δημιουργεί τους πίνακες loans και transactions. */
    public static void initialize() throws SQLException {
        String[] statements = {
            "IF COL_LENGTH('dbo.individual_customers', 'balance') IS NULL "
          + "ALTER TABLE dbo.individual_customers ADD balance DECIMAL(18,2) NOT NULL "
          + "CONSTRAINT DF_individual_balance DEFAULT 0",

            "IF COL_LENGTH('dbo.business_customers', 'balance') IS NULL "
          + "ALTER TABLE dbo.business_customers ADD balance DECIMAL(18,2) NOT NULL "
          + "CONSTRAINT DF_business_balance DEFAULT 0",

            "IF OBJECT_ID('dbo.loans', 'U') IS NULL "
          + "CREATE TABLE dbo.loans ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " customer_type TINYINT NOT NULL,"          // 1 = ιδιώτης, 2 = επιχείρηση
          + " customer_id INT NOT NULL,"
          + " iban CHAR(27) NOT NULL,"
          + " amount DECIMAL(18,2) NOT NULL,"
          + " term_years INT NOT NULL,"
          + " interest_rate DECIMAL(6,3) NOT NULL,"
          + " monthly_payment DECIMAL(18,2) NOT NULL,"
          + " total_cost DECIMAL(18,2) NOT NULL,"
          + " purpose NVARCHAR(100) NOT NULL,"
          + " approved_by NVARCHAR(50) NOT NULL,"
          + " created_at DATETIME2 DEFAULT SYSDATETIME())",

            "IF OBJECT_ID('dbo.transactions', 'U') IS NULL "
          + "CREATE TABLE dbo.transactions ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " iban CHAR(27) NOT NULL,"
          + " tx_type NVARCHAR(30) NOT NULL,"
          + " amount DECIMAL(18,2) NOT NULL,"
          + " balance_after DECIMAL(18,2) NOT NULL,"
          + " description NVARCHAR(200) NULL,"
          + " loan_id INT NULL,"
          + " created_at DATETIME2 DEFAULT SYSDATETIME())",

            "IF COL_LENGTH('dbo.loans', 'installments_paid') IS NULL "
          + "ALTER TABLE dbo.loans ADD installments_paid INT NOT NULL CONSTRAINT DF_loans_installments DEFAULT 0",

            "IF COL_LENGTH('dbo.loans', 'paid_total') IS NULL "
          + "ALTER TABLE dbo.loans ADD paid_total DECIMAL(18,2) NOT NULL CONSTRAINT DF_loans_paid DEFAULT 0",

            "IF COL_LENGTH('dbo.loans', 'status') IS NULL "
          + "ALTER TABLE dbo.loans ADD status NVARCHAR(10) NOT NULL CONSTRAINT DF_loans_status DEFAULT 'ACTIVE'",

            "IF COL_LENGTH('dbo.transactions', 'performed_by') IS NULL "
          + "ALTER TABLE dbo.transactions ADD performed_by NVARCHAR(50) NULL"
        };
        try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
        LedgerDAO.initialize();      // πίνακες γενικού καθολικού (ώστε να υπάρχουν πριν από κάθε εγγραφή)
    }

    /**
     * Εκταμιεύει ένα δάνειο: καταχωρεί το δάνειο, πιστώνει το ποσό στον λογαριασμό του πελάτη και
     * γράφει την κίνηση. Όλα γίνονται σε ΜΙΑ συναλλαγή: ή γίνονται όλα ή τίποτα.
     */
    public static Disbursement disburseLoan(int customerType, int customerId, LoanTerms terms,
                                            String approvedBy) throws SQLException {
        String table = customerType == TYPE_INDIVIDUAL ? "individual_customers" : "business_customers";
        BigDecimal amount = terms.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new SQLException("Το ποσό του δανείου πρέπει να είναι θετικό.");
        }
        BigDecimal monthly = BigDecimal.valueOf(terms.monthlyPayment()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = BigDecimal.valueOf(terms.ratePercent()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = AmortizationSchedule.total(
                AmortizationSchedule.build(amount, rate, terms.termYears() * 12, monthly));

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1) Κλείδωμα της εγγραφής του πελάτη και ανάγνωση του IBAN
                String iban = lockAndGetIban(c, table, customerType, customerId);

                // 2) Πίστωση του λογαριασμού και ανάγνωση του νέου υπολοίπου
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance + ? WHERE id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, customerId);
                    ps.executeUpdate();
                }
                BigDecimal newBalance;
                try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                    ps.setInt(1, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        newBalance = rs.getBigDecimal(1);
                    }
                }

                // 3) Καταχώρηση του δανείου
                long loanId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO loans (customer_type, customer_id, iban, amount, term_years, interest_rate, "
                      + "monthly_payment, total_cost, purpose, approved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerType);
                    ps.setInt(2, customerId);
                    ps.setString(3, iban);
                    ps.setBigDecimal(4, amount);
                    ps.setInt(5, terms.termYears());
                    ps.setBigDecimal(6, rate);
                    ps.setBigDecimal(7, monthly);
                    ps.setBigDecimal(8, totalRepayment);
                    ps.setString(9, terms.purpose());
                    ps.setString(10, approvedBy);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Δεν επιστράφηκε αριθμός δανείου.");
                        }
                        loanId = keys.getLong(1);
                    }
                }

                // 4) Κίνηση λογαριασμού
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transactions (iban, tx_type, amount, balance_after, description, loan_id, performed_by) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, iban);
                    ps.setString(2, "LOAN_DISBURSEMENT");
                    ps.setBigDecimal(3, amount);
                    ps.setBigDecimal(4, newBalance);
                    ps.setString(5, "Εκταμίευση δανείου #" + loanId + " (" + terms.purpose() + ")");
                    ps.setLong(6, loanId);
                    ps.setString(7, approvedBy);
                    ps.executeUpdate();
                }

                // 5) Λογιστική εγγραφή, στην ίδια συναλλαγή
                LedgerService.postDisbursement(c, customerType, loanId, amount, approvedBy);

                c.commit();
                return new Disbursement(loanId, iban, newBalance);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ===================== ΚΑΤΑΘΕΣΗ / ΑΝΑΛΗΨΗ =====================

    public static final String TX_DEPOSIT = "DEPOSIT";
    public static final String TX_WITHDRAWAL = "WITHDRAWAL";

    /** Ανεπαρκές υπόλοιπο για την ανάληψη. */
    public static class InsufficientFundsException extends SQLException {
        public InsufficientFundsException(String message) { super(message); }
    }

    /**
     * Κατάθεση ή ανάληψη. Η ανάληψη γίνεται μόνο αν το υπόλοιπο επαρκεί (ο έλεγχος γίνεται μέσα στην ίδια
     * εντολή UPDATE, άρα δεν μπορεί να βγει αρνητικό υπόλοιπο ούτε με ταυτόχρονες κινήσεις).
     * Επιστρέφει το νέο υπόλοιπο.
     */
    public static BigDecimal applyTransaction(int customerType, int customerId, String txType,
                                              BigDecimal amount, String note, String performedBy) throws SQLException {
        boolean deposit = TX_DEPOSIT.equals(txType);
        if (!deposit && !TX_WITHDRAWAL.equals(txType)) {
            throw new SQLException("Άγνωστος τύπος κίνησης: " + txType);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new SQLException("Το ποσό πρέπει να είναι θετικό.");
        }
        String table = customerType == TYPE_INDIVIDUAL ? "individual_customers" : "business_customers";

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                String iban = lockAndGetIban(c, table, customerType, customerId);
                if (!deposit) {
                    checkDailyLimit(c, iban, amount);
                }

                String sql = deposit
                        ? "UPDATE " + table + " SET balance = balance + ? WHERE id = ?"
                        : "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?";
                int updated;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, customerId);
                    if (!deposit) {
                        ps.setBigDecimal(3, amount);
                    }
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    throw new InsufficientFundsException("Ανεπαρκές υπόλοιπο για την ανάληψη.");
                }

                BigDecimal newBalance;
                try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                    ps.setInt(1, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        newBalance = rs.getBigDecimal(1);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transactions (iban, tx_type, amount, balance_after, description, performed_by) "
                      + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, iban);
                    ps.setString(2, txType);
                    ps.setBigDecimal(3, deposit ? amount : amount.negate());   // οι αναλήψεις αποθηκεύονται με αρνητικό πρόσημο
                    ps.setBigDecimal(4, newBalance);
                    ps.setString(5, (deposit ? "Κατάθεση" : "Ανάληψη") + (note == null || note.isBlank() ? "" : " – " + note.trim()));
                    ps.setString(6, performedBy);
                    ps.executeUpdate();
                }

                LedgerService.postCashMovement(c, customerType, deposit, amount,
                        nameOf(c, customerType, customerId), iban, note, performedBy);

                c.commit();
                return newBalance;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Οι τελευταίες κινήσεις ενός λογαριασμού (οι νεότερες πρώτα). */
    public static List<TransactionRecord> recentTransactions(String iban, int limit) throws SQLException {
        List<TransactionRecord> list = new ArrayList<>();
        if (iban == null || iban.isBlank()) {
            return list;
        }
        String sql = "SELECT TOP (?) created_at, tx_type, amount, balance_after, description "
                   + "FROM transactions WHERE iban = ? ORDER BY id DESC";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setString(2, iban.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TransactionRecord(
                            rs.getTimestamp(1).toLocalDateTime(), rs.getString(2),
                            rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getString(5)));
                }
            }
        }
        return list;
    }

    /** Κλειδώνει την εγγραφή του πελάτη και επιστρέφει το IBAN του (αν λείπει, το δημιουργεί). */
    static String lockAndGetIban(Connection c, String table, int customerType, int customerId)
            throws SQLException {
        String iban;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT iban FROM " + table + " WITH (UPDLOCK, ROWLOCK) WHERE id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Ο πελάτης δεν βρέθηκε στη βάση.");
                }
                iban = rs.getString(1);
            }
        }
        if (iban == null || iban.isBlank()) {          // πελάτης που εγγράφηκε πριν προστεθεί το IBAN
            iban = IbanGenerator.generate(customerType, customerId);
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + table + " SET iban = ? WHERE id = ?")) {
                ps.setString(1, iban);
                ps.setInt(2, customerId);
                ps.executeUpdate();
            }
        }
        return iban.trim();
    }

    // ===================== ΜΕΤΑΦΟΡΑ ΜΕΤΑΞΥ ΛΟΓΑΡΙΑΣΜΩΝ =====================

    /** Υπέρβαση του ημερήσιου ορίου εξερχόμενων κινήσεων. */
    public static class LimitExceededException extends SQLException {
        public LimitExceededException(String message) { super(message); }
    }

    private record Account(int type, int id, String name) { }

    /**
     * Μεταφορά χρημάτων προς άλλον λογαριασμό της τράπεζας (με IBAN). Η χρέωση, η πίστωση και οι δύο κινήσεις
     * γίνονται σε μία συναλλαγή. Τα δύο κλειδώματα γίνονται πάντα με την ίδια σειρά, για να μην κολλήσουν
     * (deadlock) δύο ταυτόχρονες μεταφορές.
     */
    public static TransferResult transfer(int srcType, int srcId, String destIbanRaw, BigDecimal amount,
                                          String note, String performedBy) throws SQLException {
        String destIban = destIbanRaw == null ? "" : destIbanRaw.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
        if (!IbanGenerator.isValid(destIban)) {
            throw new SQLException("Μη έγκυρος IBAN προορισμού.");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new SQLException("Το ποσό πρέπει να είναι θετικό.");
        }
        String srcTable = tableOf(srcType);

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                Account dest = findAccountByIban(c, destIban);
                if (dest == null) {
                    throw new SQLException("Ο IBAN προορισμού δεν ανήκει σε λογαριασμό της τράπεζας.");
                }
                if (dest.type() == srcType && dest.id() == srcId) {
                    throw new SQLException("Ο λογαριασμός προορισμού είναι ο ίδιος με τον λογαριασμό πηγής.");
                }
                String dstTable = tableOf(dest.type());

                boolean srcFirst = srcType < dest.type() || (srcType == dest.type() && srcId < dest.id());
                String srcIban;
                if (srcFirst) {
                    srcIban = lockAndGetIban(c, srcTable, srcType, srcId);
                    lockRow(c, dstTable, dest.id());
                } else {
                    lockRow(c, dstTable, dest.id());
                    srcIban = lockAndGetIban(c, srcTable, srcType, srcId);
                }
                if (srcIban.equals(destIban)) {
                    throw new SQLException("Ο λογαριασμός προορισμού είναι ο ίδιος με τον λογαριασμό πηγής.");
                }
                String srcName = nameOf(c, srcType, srcId);

                checkDailyLimit(c, srcIban, amount);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + srcTable + " SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, srcId);
                    ps.setBigDecimal(3, amount);
                    if (ps.executeUpdate() == 0) {
                        throw new InsufficientFundsException("Ανεπαρκές υπόλοιπο για τη μεταφορά.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + dstTable + " SET balance = balance + ? WHERE id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, dest.id());
                    ps.executeUpdate();
                }
                BigDecimal srcBalance = readBalance(c, srcTable, srcId);
                BigDecimal dstBalance = readBalance(c, dstTable, dest.id());

                String extra = note == null || note.isBlank() ? "" : " – " + note.trim();
                insertTransaction(c, srcIban, "TRANSFER_OUT", amount.negate(), srcBalance,
                        "Μεταφορά προς " + dest.name() + " (" + IbanGenerator.format(destIban) + ")" + extra,
                        null, performedBy);
                insertTransaction(c, destIban, "TRANSFER_IN", amount, dstBalance,
                        "Μεταφορά από " + srcName + " (" + IbanGenerator.format(srcIban) + ")" + extra,
                        null, performedBy);

                LedgerService.postTransfer(c, srcType, dest.type(), amount, performedBy);

                c.commit();
                return new TransferResult(srcBalance, dest.name(), destIban);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ===================== ΒΟΗΘΗΤΙΚΑ =====================

    /** Λογαριασμός που βρέθηκε με IBAN ή αριθμό λογαριασμού. */
    public record AccountMatch(int type, int id, String name, String afm, String iban) {
        public String typeLabel() { return type == TYPE_INDIVIDUAL ? "Ιδιώτης" : "Επιχείρηση"; }
    }

    /**
     * Βρίσκει λογαριασμούς με πλήρες IBAN (27 χαρακτήρες) ή με αριθμό λογαριασμού (10 τελευταία ψηφία του IBAN).
     * Με IBAN επιστρέφει το πολύ έναν. Με αριθμό λογαριασμού μπορεί να επιστρέψει δύο, αν ένας ιδιώτης και μια
     * επιχείρηση έχουν ίδια τα 10 τελευταία ψηφία (ο τύπος πελάτη βρίσκεται στα προηγούμενα ψηφία του IBAN).
     */
    public static List<AccountMatch> findByIbanOrAccountNumber(String cleanedInput) throws SQLException {
        boolean byIban = !IbanGenerator.isAccountNumber(cleanedInput);
        String cond = byIban ? "iban = ?" : "RIGHT(RTRIM(iban), " + IbanGenerator.ACCOUNT_NUMBER_LENGTH + ") = ?";
        String sql = "SELECT 1, id, first_name + ' ' + last_name, afm, iban FROM individual_customers WHERE " + cond
                   + " UNION ALL SELECT 2, id, company_name, afm, iban FROM business_customers WHERE " + cond;
        List<AccountMatch> out = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cleanedInput);
            ps.setString(2, cleanedInput);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AccountMatch(rs.getInt(1), rs.getInt(2), rs.getString(3),
                            rs.getString(4) == null ? "" : rs.getString(4).trim(),
                            rs.getString(5) == null ? "" : rs.getString(5).trim()));
                }
            }
        }
        return out;
    }

    private static String tableOf(int type) {
        return type == TYPE_INDIVIDUAL ? "individual_customers" : "business_customers";
    }

    /** Ελέγχει ότι οι αναλήψεις + μεταφορές της ημέρας δεν ξεπερνούν το ημερήσιο όριο. */
    static void checkDailyLimit(Connection c, String iban, BigDecimal amount) throws SQLException {
        String sql = "SELECT COALESCE(SUM(-amount), 0) FROM transactions WHERE iban = ? "
                   + "AND tx_type IN ('WITHDRAWAL', 'TRANSFER_OUT') "
                   + "AND created_at >= CAST(CAST(SYSDATETIME() AS DATE) AS DATETIME2)";
        BigDecimal used;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, iban);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                used = rs.getBigDecimal(1);
            }
        }
        if (used.add(amount).compareTo(Limits.DAILY_OUTGOING_LIMIT) > 0) {
            BigDecimal left = Limits.DAILY_OUTGOING_LIMIT.subtract(used).max(BigDecimal.ZERO);
            java.util.Locale gr = java.util.Locale.forLanguageTag("el-GR");
            throw new LimitExceededException(String.format(gr,
                    "Υπέρβαση ημερήσιου ορίου αναλήψεων/μεταφορών (%,.2f €). Διαθέσιμο σήμερα: %,.2f €.",
                    Limits.DAILY_OUTGOING_LIMIT, left));
        }
    }

    static BigDecimal readBalance(Connection c, String table, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    static void insertTransaction(Connection c, String iban, String txType, BigDecimal amount, BigDecimal balanceAfter,
                                  String description, Long loanId, String performedBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (iban, tx_type, amount, balance_after, description, loan_id, performed_by) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, iban);
            ps.setString(2, txType);
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, balanceAfter);
            ps.setString(5, description);
            if (loanId == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, loanId);
            ps.setString(7, performedBy);
            ps.executeUpdate();
        }
    }

    private static void lockRow(Connection c, String table, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + table + " WITH (UPDLOCK, ROWLOCK) WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeQuery().close();
        }
    }

    private static Account findAccountByIban(Connection c, String iban) throws SQLException {
        String sql = "SELECT 1, id, first_name + ' ' + last_name FROM individual_customers WHERE iban = ? "
                   + "UNION ALL SELECT 2, id, company_name FROM business_customers WHERE iban = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, iban);
            ps.setString(2, iban);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Account(rs.getInt(1), rs.getInt(2), rs.getString(3)) : null;
            }
        }
    }

    private static String nameOf(Connection c, int type, int id) throws SQLException {
        String sql = type == TYPE_INDIVIDUAL
                ? "SELECT first_name + ' ' + last_name FROM individual_customers WHERE id = ?"
                : "SELECT company_name FROM business_customers WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }
}
