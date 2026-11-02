package bank.ledger;

import java.util.List;

/**
 * Σχέδιο λογαριασμών της τράπεζας (ΑΠΕΙΚΟΝΙΣΤΙΚΟ, για εκπαιδευτικούς σκοπούς).
 *
 * ΔΕΝ είναι το επίσημο Κλαδικό Λογιστικό Σχέδιο Τραπεζών (ΠΔ 384/1992). Ακολουθεί μόνο τη γενική διάρθρωση του
 * ΚΛΣΤ σε ομάδες, με το ενεργητικό στις ομάδες 1-3: 1 Πάγιο ενεργητικό, 2 Χορηγήσεις, 3 Απαιτήσεις, χρεόγραφα και
 * διαθέσιμα. Οι κωδικοί και οι ονομασίες των δευτεροβάθμιων λογαριασμών, καθώς και η χρήση των ομάδων 4-5, είναι
 * επιλογές του project και δεν πρέπει να χρησιμοποιηθούν ως πραγματικά λογιστικά στοιχεία.
 */
public final class ChartOfAccounts {
    private ChartOfAccounts() { }

    public static final int CUSTOMER_INDIVIDUAL = 1;
    public static final int CUSTOMER_BUSINESS = 2;

    public static final String LOANS_INDIVIDUALS = "20.00";
    public static final String LOANS_BUSINESSES = "20.01";
    public static final String CASH = "30.00";
    public static final String CENTRAL_BANK = "31.00";
    public static final String GOV_BONDS = "32.00";
    public static final String CORP_BONDS = "32.01";
    public static final String EQUITIES = "32.02";
    public static final String DEPOSITS_INDIVIDUALS = "40.00";
    public static final String DEPOSITS_BUSINESSES = "40.01";
    public static final String SHARE_CAPITAL = "50.00";
    public static final String OPENING_DIFFERENCE = "50.01";
    public static final String INTEREST_INCOME = "70.00";

    private static GlAccount head(String code, String name, AccountType t) {
        return new GlAccount(code, name, code.charAt(0) - '0', t, null);
    }

    private static GlAccount leaf(String code, String name, AccountType t) {
        return new GlAccount(code, name, code.charAt(0) - '0', t, code.substring(0, 2));
    }

    public static final List<GlAccount> DEFAULT = List.of(
            head("10", "Ακίνητα", AccountType.ASSET),
            leaf("10.00", "Οικόπεδα και κτίρια", AccountType.ASSET),
            head("11", "Εξοπλισμός", AccountType.ASSET),
            leaf("11.00", "Έπιπλα και εξοπλισμός", AccountType.ASSET),

            head("20", "Χορηγήσεις σε πελάτες", AccountType.ASSET),
            leaf(LOANS_INDIVIDUALS, "Χορηγήσεις σε ιδιώτες", AccountType.ASSET),
            leaf(LOANS_BUSINESSES, "Χορηγήσεις σε επιχειρήσεις", AccountType.ASSET),

            head("30", "Ταμείο", AccountType.ASSET),
            leaf(CASH, "Ταμείο (μετρητά)", AccountType.ASSET),
            head("31", "Διαθέσιμα στην Κεντρική Τράπεζα", AccountType.ASSET),
            leaf(CENTRAL_BANK, "Καταθέσεις στην Τράπεζα της Ελλάδος", AccountType.ASSET),
            head("32", "Χρεόγραφα και μετοχές", AccountType.ASSET),
            leaf(GOV_BONDS, "Κρατικά ομόλογα", AccountType.ASSET),
            leaf(CORP_BONDS, "Εταιρικά ομόλογα", AccountType.ASSET),
            leaf(EQUITIES, "Μετοχές (εισηγμένες)", AccountType.ASSET),

            head("40", "Καταθέσεις πελατών", AccountType.LIABILITY),
            leaf(DEPOSITS_INDIVIDUALS, "Καταθέσεις ιδιωτών", AccountType.LIABILITY),
            leaf(DEPOSITS_BUSINESSES, "Καταθέσεις επιχειρήσεων", AccountType.LIABILITY),

            head("50", "Ίδια κεφάλαια", AccountType.EQUITY),
            leaf(SHARE_CAPITAL, "Μετοχικό κεφάλαιο", AccountType.EQUITY),
            leaf(OPENING_DIFFERENCE, "Διαφορά αρχικών υπολοίπων", AccountType.EQUITY),

            head("70", "Τόκοι και έσοδα από χορηγήσεις", AccountType.INCOME),
            leaf(INTEREST_INCOME, "Τόκοι δανείων", AccountType.INCOME)
    );

    public static String loansOf(int customerType) {
        return customerType == CUSTOMER_INDIVIDUAL ? LOANS_INDIVIDUALS : LOANS_BUSINESSES;
    }

    public static String depositsOf(int customerType) {
        return customerType == CUSTOMER_INDIVIDUAL ? DEPOSITS_INDIVIDUALS : DEPOSITS_BUSINESSES;
    }
}
