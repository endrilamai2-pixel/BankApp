package bank.kyc;

import bank.registration.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Ιστορικό ελέγχων KYC (πίνακας kyc_checks): ποιος, πότε, για ποιο ΑΦΜ, με ποιο αποτέλεσμα. */
public class KycDAO {

    private static volatile boolean schemaReady = false;

    private static void ensureSchema() throws SQLException {
        if (!schemaReady) {
            try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
                st.execute(CREATE_TABLE);
            }
            schemaReady = true;
        }
    }

    /** Χρησιμοποιείται και από το DatabaseManager.initialize(). */
    public static final String CREATE_TABLE =
            "IF OBJECT_ID('dbo.kyc_checks', 'U') IS NULL "
          + "CREATE TABLE dbo.kyc_checks ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " afm CHAR(9) NOT NULL,"
          + " customer_type NVARCHAR(20) NOT NULL,"
          + " status NVARCHAR(20) NOT NULL,"
          + " source NVARCHAR(100) NOT NULL,"
          + " live_check BIT NOT NULL,"
          + " aade_name NVARCHAR(300) NULL,"
          + " details NVARCHAR(1000) NULL,"
          + " checked_by NVARCHAR(100) NOT NULL,"
          + " registered BIT NOT NULL,"
          + " checked_at DATETIME2 DEFAULT SYSDATETIME())";

    /**
     * Καταγράφει έναν έλεγχο.
     * @param registered αν ο πελάτης τελικά εγγράφηκε (π.χ. false όταν ο έλεγχος απέρριψε την εγγραφή)
     */
    public void log(String afm, String customerType, KycChecker.Outcome o, boolean live,
                    String user, boolean registered) throws SQLException {
        ensureSchema();
        String sql = "INSERT INTO kyc_checks (afm, customer_type, status, source, live_check, aade_name, details, "
                   + "checked_by, registered) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, afm);
            ps.setString(2, customerType);
            ps.setString(3, o.status().name());
            ps.setString(4, o.source());
            ps.setBoolean(5, live);
            ps.setString(6, o.record() == null ? null : o.record().name());
            String details = String.join(" | ", o.messages());
            ps.setString(7, details.length() > 1000 ? details.substring(0, 1000) : details);
            ps.setString(8, user);
            ps.setBoolean(9, registered);
            ps.executeUpdate();
        }
    }
}
