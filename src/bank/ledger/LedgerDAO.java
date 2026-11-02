package bank.ledger;

import bank.registration.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Γενικό καθολικό με διπλογραφία. Κάθε εγγραφή (gl_entries) έχει γραμμές (gl_lines) που ισοσκελίζουν
 * (σύνολο χρεώσεων = σύνολο πιστώσεων). Οι εγγραφές μόνο προστίθενται, δεν τροποποιούνται ούτε διαγράφονται.
 */
public final class LedgerDAO {

    private LedgerDAO() { }

    /** Μία γραμμή εγγραφής: ή χρέωση ή πίστωση, ποτέ και τα δύο. */
    public record Line(String account, BigDecimal debit, BigDecimal credit) {
        public static Line debit(String account, BigDecimal amount) { return new Line(account, amount, BigDecimal.ZERO); }
        public static Line credit(String account, BigDecimal amount) { return new Line(account, BigDecimal.ZERO, amount); }
    }

    public record AccountBalance(GlAccount account, BigDecimal debit, BigDecimal credit) {
        /** Υπόλοιπο στην κανονική πλευρά του λογαριασμού (θετικό = φυσιολογικό). */
        public BigDecimal net() {
            return account.type().debitNormal() ? debit.subtract(credit) : credit.subtract(debit);
        }
    }

    public record JournalLine(long entryId, LocalDateTime date, String description, String postedBy,
                              String accountCode, String accountName, BigDecimal debit, BigDecimal credit) { }

    public static void initialize() throws SQLException {
        String[] ddl = {
            "IF OBJECT_ID('dbo.gl_accounts', 'U') IS NULL "
          + "CREATE TABLE dbo.gl_accounts ("
          + " code VARCHAR(10) NOT NULL PRIMARY KEY,"
          + " name NVARCHAR(150) NOT NULL,"
          + " grp TINYINT NOT NULL,"
          + " account_type VARCHAR(10) NOT NULL,"
          + " parent_code VARCHAR(10) NULL)",

            "IF OBJECT_ID('dbo.gl_entries', 'U') IS NULL "
          + "CREATE TABLE dbo.gl_entries ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " entry_date DATETIME2 NOT NULL DEFAULT SYSDATETIME(),"
          + " description NVARCHAR(250) NOT NULL,"
          + " source_type VARCHAR(30) NOT NULL,"
          + " source_id BIGINT NULL,"
          + " posted_by NVARCHAR(50) NOT NULL)",

            "IF OBJECT_ID('dbo.gl_lines', 'U') IS NULL "
          + "CREATE TABLE dbo.gl_lines ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " entry_id INT NOT NULL REFERENCES dbo.gl_entries(id),"
          + " account_code VARCHAR(10) NOT NULL REFERENCES dbo.gl_accounts(code),"
          + " debit DECIMAL(18,2) NOT NULL DEFAULT 0,"
          + " credit DECIMAL(18,2) NOT NULL DEFAULT 0,"
          + " CONSTRAINT CK_gl_lines_side CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0) AND debit + credit > 0))"
        };
        String seed = "IF NOT EXISTS (SELECT 1 FROM dbo.gl_accounts WHERE code = ?) "
                    + "INSERT INTO dbo.gl_accounts (code, name, grp, account_type, parent_code) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection()) {
            try (Statement st = c.createStatement()) {
                for (String sql : ddl) st.execute(sql);
            }
            try (PreparedStatement ps = c.prepareStatement(seed)) {
                for (GlAccount a : ChartOfAccounts.DEFAULT) {
                    ps.setString(1, a.code());
                    ps.setString(2, a.code());
                    ps.setString(3, a.name());
                    ps.setInt(4, a.group());
                    ps.setString(5, a.type().name());
                    ps.setString(6, a.parentCode());
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Ελέγχει ότι η εγγραφή είναι έγκυρη και ισοσκελισμένη. Ρίχνει IllegalArgumentException αλλιώς. */
    static void validate(List<Line> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("Η εγγραφή χρειάζεται τουλάχιστον δύο γραμμές.");
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Line l : lines) {
            boolean d = l.debit().signum() > 0;
            boolean c = l.credit().signum() > 0;
            if (d == c || l.debit().signum() < 0 || l.credit().signum() < 0) {
                throw new IllegalArgumentException("Κάθε γραμμή πρέπει να έχει είτε χρέωση είτε πίστωση (θετικό ποσό): "
                        + l.account());
            }
            debit = debit.add(l.debit());
            credit = credit.add(l.credit());
        }
        if (debit.compareTo(credit) != 0) {
            throw new IllegalArgumentException("Η εγγραφή δεν ισοσκελίζει: χρεώσεις " + debit + ", πιστώσεις " + credit);
        }
    }

    /**
     * Καταχωρεί μια εγγραφή στη ΣΥΝΑΛΛΑΓΗ του καλούντος (δεν κάνει commit), ώστε να αποθηκεύεται ή να ακυρώνεται
     * μαζί με την πράξη που την προκάλεσε (π.χ. την εκταμίευση δανείου).
     */
    public static long post(Connection c, String description, String sourceType, Long sourceId,
                            String user, List<Line> lines) throws SQLException {
        validate(lines);
        long entryId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gl_entries (description, source_type, source_id, posted_by) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, description);
            ps.setString(2, sourceType);
            if (sourceId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, sourceId);
            ps.setString(4, user);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Δεν επιστράφηκε αριθμός εγγραφής.");
                entryId = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gl_lines (entry_id, account_code, debit, credit) VALUES (?, ?, ?, ?)")) {
            for (Line l : lines) {
                ps.setLong(1, entryId);
                ps.setString(2, l.account());
                ps.setBigDecimal(3, l.debit());
                ps.setBigDecimal(4, l.credit());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return entryId;
    }

    /** Σύνολα χρεώσεων/πιστώσεων ανά λογαριασμό (όλοι οι λογαριασμοί, και όσοι δεν έχουν κίνηση). */
    public static List<AccountBalance> balances() throws SQLException {
        String sql = "SELECT a.code, a.name, a.grp, a.account_type, a.parent_code, "
                   + "COALESCE(SUM(l.debit), 0), COALESCE(SUM(l.credit), 0) "
                   + "FROM gl_accounts a LEFT JOIN gl_lines l ON l.account_code = a.code "
                   + "GROUP BY a.code, a.name, a.grp, a.account_type, a.parent_code ORDER BY a.code";
        List<AccountBalance> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GlAccount a = new GlAccount(rs.getString(1).trim(), rs.getString(2), rs.getInt(3),
                        AccountType.valueOf(rs.getString(4).trim()), rs.getString(5) == null ? null : rs.getString(5).trim());
                list.add(new AccountBalance(a, rs.getBigDecimal(6), rs.getBigDecimal(7)));
            }
        }
        return list;
    }

    /** Υπόλοιπο (χρεώσεις − πιστώσεις) λογαριασμών ενεργητικού, μέσα στη συναλλαγή του καλούντος. */
    public static BigDecimal netDebit(Connection c, String... accountCodes) throws SQLException {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < accountCodes.length; i++) in.append(i == 0 ? "?" : ", ?");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(debit) - SUM(credit), 0) FROM gl_lines WHERE account_code IN (" + in + ")")) {
            for (int i = 0; i < accountCodes.length; i++) ps.setString(i + 1, accountCodes[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public static int entryCount() throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM gl_entries");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Οι τελευταίες γραμμές του ημερολογίου (νεότερες πρώτα). */
    public static List<JournalLine> journal(int limit) throws SQLException {
        String sql = "SELECT TOP (?) e.id, e.entry_date, e.description, e.posted_by, l.account_code, a.name, l.debit, l.credit "
                   + "FROM gl_lines l JOIN gl_entries e ON e.id = l.entry_id JOIN gl_accounts a ON a.code = l.account_code "
                   + "ORDER BY e.id DESC, l.id";
        List<JournalLine> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new JournalLine(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3),
                            rs.getString(4), rs.getString(5).trim(), rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8)));
                }
            }
        }
        return list;
    }
}
