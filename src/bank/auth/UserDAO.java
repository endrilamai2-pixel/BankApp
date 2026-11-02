package bank.auth;

import bank.registration.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Χρήστες (υπάλληλοι) που έχουν δικαίωμα εισόδου στην εφαρμογή. */
public final class UserDAO {

    // Χρησιμοποιείται όταν ο χρήστης δεν υπάρχει, ώστε ο χρόνος απόκρισης να μην αποκαλύπτει αν υπάρχει το username
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password".toCharArray());

    private UserDAO() { }

    public static void initialize() throws SQLException {
        String sql = "IF OBJECT_ID('dbo.bank_users', 'U') IS NULL "
                   + "CREATE TABLE dbo.bank_users ("
                   + " id INT IDENTITY(1,1) PRIMARY KEY,"
                   + " username NVARCHAR(50) NOT NULL UNIQUE,"
                   + " password_hash NVARCHAR(255) NOT NULL,"
                   + " created_at DATETIME2 DEFAULT SYSDATETIME())";
        try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    public static boolean hasUsers() throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bank_users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public static void createUser(String username, char[] password) throws SQLException {
        String sql = "INSERT INTO bank_users (username, password_hash) VALUES (?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hash(password));
            ps.executeUpdate();
        }
    }

    /** Επιστρέφει true μόνο αν το username υπάρχει και ο κωδικός είναι σωστός. */
    public static boolean authenticate(String username, char[] password) throws SQLException {
        String stored = null;
        String sql = "SELECT password_hash FROM bank_users WHERE username = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stored = rs.getString(1);
                }
            }
        }
        boolean passwordOk = PasswordHasher.verify(password, stored != null ? stored : DUMMY_HASH);
        return stored != null && passwordOk;
    }
}
