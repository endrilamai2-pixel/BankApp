package bank.customers;

import bank.account.AccountDAO;
import bank.registration.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ανάγνωση της λίστας όλων των πελατών (ιδιωτών και επιχειρήσεων) μαζί με την κατάσταση KYC. Μόνο ανάγνωση. */
public final class CustomerListDAO {

    /** Κατάσταση KYC, με βάση τον τελευταίο έλεγχο του ΑΦΜ στον πίνακα kyc_checks. */
    public enum KycState {
        NONE("Χωρίς έλεγχο"),
        VERIFIED("Επαληθευμένος"),
        PENDING("Εκκρεμής"),
        REJECTED("Απορρίφθηκε – εγγράφηκε");

        public final String label;
        KycState(String label) { this.label = label; }

        /** Χρειάζεται παρακολούθηση: ο έλεγχος δεν έχει ολοκληρωθεί επιτυχώς. */
        public boolean needsFollowUp() { return this == PENDING || this == REJECTED || this == NONE; }

        static KycState from(String status) {
            if (status == null) return NONE;
            return switch (status.trim()) {
                case "VERIFIED" -> VERIFIED;
                case "REJECTED" -> REJECTED;
                default -> PENDING;      // WARNING, UNAVAILABLE
            };
        }
    }

    /** Μία γραμμή της λίστας. */
    public record Row(boolean business, String name, String afm, String iban, BigDecimal balance,
                      int activeLoans, KycState kyc, LocalDateTime registeredAt) {
        public String typeLabel() { return business ? "Επιχείρηση" : "Ιδιώτης"; }
    }

    private static volatile boolean schemaReady = false;

    private static void ensureSchema() throws SQLException {
        if (!schemaReady) {
            AccountDAO.initialize();      // φροντίζει για τη στήλη balance και τον πίνακα loans
            schemaReady = true;
        }
    }

    private static final String SQL =
        "SELECT 0 AS is_business, i.first_name + N' ' + i.last_name AS name, i.afm, i.iban, i.balance, i.created_at, "
      + "  (SELECT COUNT(*) FROM loans l WHERE l.customer_type = 1 AND l.customer_id = i.id AND RTRIM(l.status) <> 'PAID') AS active_loans, "
      + "  k.status AS kyc_status "
      + "FROM individual_customers i "
      + "OUTER APPLY (SELECT TOP 1 status FROM kyc_checks WHERE afm = i.afm ORDER BY id DESC) k "
      + "UNION ALL "
      + "SELECT 1, b.company_name, b.afm, b.iban, b.balance, b.created_at, "
      + "  (SELECT COUNT(*) FROM loans l WHERE l.customer_type = 2 AND l.customer_id = b.id AND RTRIM(l.status) <> 'PAID'), "
      + "  k.status "
      + "FROM business_customers b "
      + "OUTER APPLY (SELECT TOP 1 status FROM kyc_checks WHERE afm = b.afm ORDER BY id DESC) k "
      + "ORDER BY name";

    public List<Row> listAll() throws SQLException {
        ensureSchema();
        List<Row> rows = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                BigDecimal balance = rs.getBigDecimal("balance");
                rows.add(new Row(
                        rs.getInt("is_business") == 1,
                        rs.getString("name"),
                        rs.getString("afm") == null ? "" : rs.getString("afm").trim(),
                        rs.getString("iban") == null ? "" : rs.getString("iban").trim(),
                        balance == null ? BigDecimal.ZERO : balance,
                        rs.getInt("active_loans"),
                        KycState.from(rs.getString("kyc_status")),
                        ts == null ? null : ts.toLocalDateTime()));
            }
        }
        return rows;
    }
}
