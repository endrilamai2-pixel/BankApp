package bank.registration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Διαχείριση της σύνδεσης με τη βάση δεδομένων (Microsoft SQL Server).
 *
 * Οι ρυθμίσεις διαβάζονται από μεταβλητές περιβάλλοντος ή ιδιότητες -D (ΔΕΝ υπάρχουν κωδικοί μέσα στον κώδικα):
 *   BANK_DB_URL      / -Dbank.db.url        (προαιρετικό) προεπιλογή: τοπικός SQL Server, βάση BankDB
 *   BANK_DB_USER     / -Dbank.db.user       (προαιρετικό) προεπιλογή: bankuser
 *   BANK_DB_PASSWORD / -Dbank.db.password   ΥΠΟΧΡΕΩΤΙΚΟ
 */
public final class DatabaseManager {

    private static final String DEFAULT_URL =
        "jdbc:sqlserver://localhost:1433;databaseName=BankDB;encrypt=true;trustServerCertificate=true";
    private static final String DEFAULT_USER = "bankuser";

    private DatabaseManager() { }

    public static Connection getConnection() throws SQLException {
        String password = read("BANK_DB_PASSWORD", "bank.db.password", null);
        if (password == null) {
            throw new SQLException("Δεν έχει οριστεί κωδικός βάσης. Όρισε τη μεταβλητή περιβάλλοντος BANK_DB_PASSWORD "
                    + "(ή -Dbank.db.password=...). Δες το README.");
        }
        return DriverManager.getConnection(
                read("BANK_DB_URL", "bank.db.url", DEFAULT_URL),
                read("BANK_DB_USER", "bank.db.user", DEFAULT_USER),
                password);
    }

    private static String read(String env, String prop, String fallback) {
        String v = System.getenv(env);
        if (v == null || v.isBlank()) v = System.getProperty(prop);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /** Δημιουργεί τους πίνακες αν δεν υπάρχουν και προσθέτει τη στήλη iban αν λείπει. */
    public static void initialize() throws SQLException {
        String individuals =
            "IF OBJECT_ID('dbo.individual_customers', 'U') IS NULL "
          + "CREATE TABLE dbo.individual_customers ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " first_name NVARCHAR(100) NOT NULL,"
          + " last_name NVARCHAR(100) NOT NULL,"
          + " id_number NVARCHAR(50) NOT NULL UNIQUE,"
          + " afm CHAR(9) NOT NULL UNIQUE,"
          + " profession NVARCHAR(100) NOT NULL,"
          + " home_address NVARCHAR(200) NOT NULL,"
          + " iban CHAR(27) NULL,"
          + " created_at DATETIME2 DEFAULT SYSDATETIME())";

        String businesses =
            "IF OBJECT_ID('dbo.business_customers', 'U') IS NULL "
          + "CREATE TABLE dbo.business_customers ("
          + " id INT IDENTITY(1,1) PRIMARY KEY,"
          + " company_name NVARCHAR(150) NOT NULL,"
          + " headquarters NVARCHAR(200) NOT NULL,"
          + " afm CHAR(9) NOT NULL UNIQUE,"
          + " activity NVARCHAR(150) NOT NULL,"
          + " iban CHAR(27) NULL,"
          + " created_at DATETIME2 DEFAULT SYSDATETIME())";

        // Για πίνακες που φτιάχτηκαν πριν προστεθεί το IBAN
        String addIbanIndividuals =
            "IF COL_LENGTH('dbo.individual_customers', 'iban') IS NULL "
          + "ALTER TABLE dbo.individual_customers ADD iban CHAR(27) NULL";
        String addIbanBusinesses =
            "IF COL_LENGTH('dbo.business_customers', 'iban') IS NULL "
          + "ALTER TABLE dbo.business_customers ADD iban CHAR(27) NULL";

        // Νέα στοιχεία ιδιώτη. Οι στήλες είναι nullable για συμβατότητα με παλιές εγγραφές.
        String addDateOfBirth =
            "IF COL_LENGTH('dbo.individual_customers', 'date_of_birth') IS NULL "
          + "ALTER TABLE dbo.individual_customers ADD date_of_birth DATE NULL";
        String addPhone =
            "IF COL_LENGTH('dbo.individual_customers', 'phone') IS NULL "
          + "ALTER TABLE dbo.individual_customers ADD phone NVARCHAR(30) NULL";

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(individuals);
            st.execute(businesses);
            st.execute(addIbanIndividuals);
            st.execute(addIbanBusinesses);
            st.execute(addDateOfBirth);
            st.execute(addPhone);
            st.execute(bank.kyc.KycDAO.CREATE_TABLE);
        }
    }
}
