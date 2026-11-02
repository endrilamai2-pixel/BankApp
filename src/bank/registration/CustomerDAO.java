package bank.registration;

import bank.account.AccountDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

/** Αποθήκευση και έλεγχοι πελατών στη βάση δεδομένων. */
public class CustomerDAO {

    private static volatile boolean schemaReady = false;

    /** Φροντίζει να υπάρχει η στήλη balance (και οι πίνακες loans/transactions) πριν από τις αναζητήσεις. */
    private static void ensureSchema() throws SQLException {
        if (!schemaReady) {
            AccountDAO.initialize();
            schemaReady = true;
        }
    }

    /** Ελέγχει αν υπάρχει ήδη πελάτης (ιδιώτης ή επιχείρηση) με το ίδιο ΑΦΜ. */
    public boolean afmExists(String afm) throws SQLException {
        String sql = "SELECT 1 FROM individual_customers WHERE afm = ? "
                   + "UNION SELECT 1 FROM business_customers WHERE afm = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, afm);
            ps.setString(2, afm);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean idNumberExists(String idNumber) throws SQLException {
        String sql = "SELECT 1 FROM individual_customers WHERE id_number = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Αναζήτηση πελάτη ιδιώτη με ΑΦΜ. */
    public Optional<IndividualCustomer> findIndividualByAfm(String afm) throws SQLException {
        ensureSchema();
        String sql = "SELECT id, first_name, last_name, id_number, afm, profession, home_address, "
                   + "date_of_birth, phone, iban, balance FROM individual_customers WHERE afm = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, afm);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IndividualCustomer(
                        rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("id_number"), rs.getString("afm"), rs.getString("profession"),
                        rs.getString("home_address"),
                        rs.getDate("date_of_birth") == null ? null : rs.getDate("date_of_birth").toLocalDate(),
                        rs.getString("phone"), rs.getString("iban"), rs.getBigDecimal("balance")));
            }
        }
    }

    /** Αναζήτηση πελάτη επιχείρησης με ΑΦΜ. */
    public Optional<BusinessCustomer> findBusinessByAfm(String afm) throws SQLException {
        ensureSchema();
        String sql = "SELECT id, company_name, headquarters, afm, activity, iban, balance "
                   + "FROM business_customers WHERE afm = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, afm);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BusinessCustomer(
                        rs.getInt("id"), rs.getString("company_name"), rs.getString("headquarters"),
                        rs.getString("afm"), rs.getString("activity"), rs.getString("iban"), rs.getBigDecimal("balance")));
            }
        }
    }

    /** Αποθηκεύει ιδιώτη και επιστρέφει το IBAN που του δόθηκε. */
    public String saveIndividual(String firstName, String lastName, String idNumber,
                                 String afm, String profession, String homeAddress,
                                 LocalDate dateOfBirth, String phone) throws SQLException {
        String sql = "INSERT INTO individual_customers "
                   + "(first_name, last_name, id_number, afm, profession, home_address, date_of_birth, phone) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, firstName); ps.setString(2, lastName); ps.setString(3, idNumber);
                    ps.setString(4, afm); ps.setString(5, profession); ps.setString(6, homeAddress);
                    ps.setDate(7, Date.valueOf(dateOfBirth)); ps.setString(8, phone);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Δεν επιστράφηκε id για τη νέα εγγραφή.");
                        id = keys.getLong(1);
                    }
                }
                String iban = IbanGenerator.generate(IbanGenerator.TYPE_INDIVIDUAL, id);
                try (PreparedStatement up = c.prepareStatement("UPDATE individual_customers SET iban = ? WHERE id = ?")) {
                    up.setString(1, iban); up.setLong(2, id); up.executeUpdate();
                }
                c.commit();
                return iban;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Αποθηκεύει επιχείρηση και επιστρέφει το IBAN που της δόθηκε. */
    public String saveBusiness(String companyName, String headquarters,
                               String afm, String activity) throws SQLException {
        String sql = "INSERT INTO business_customers "
                   + "(company_name, headquarters, afm, activity) VALUES (?, ?, ?, ?)";
        return insertAndAssignIban("business_customers", IbanGenerator.TYPE_BUSINESS, sql,
                companyName, headquarters, afm, activity);
    }

    /**
     * Κάνει insert, παίρνει το id που δημιούργησε η βάση, φτιάχνει το IBAN
     * και το γράφει στην ίδια εγγραφή. Όλα σε μία συναλλαγή.
     */
    private String insertAndAssignIban(String table, int type, String insertSql,
                                       String... params) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    for (int i = 0; i < params.length; i++) {
                        ps.setString(i + 1, params[i]);
                    }
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Δεν επιστράφηκε id για τη νέα εγγραφή.");
                        }
                        id = keys.getLong(1);
                    }
                }

                String iban = IbanGenerator.generate(type, id);

                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE " + table + " SET iban = ? WHERE id = ?")) {
                    up.setString(1, iban);
                    up.setLong(2, id);
                    up.executeUpdate();
                }

                c.commit();
                return iban;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
