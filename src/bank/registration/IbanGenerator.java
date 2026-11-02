package bank.registration;

/**
 * Δημιουργία ελληνικού IBAN (27 χαρακτήρες):
 * GR + 2 ψηφία ελέγχου + 7ψήφιος κωδικός τράπεζας/καταστήματος + 16ψήφιος αριθμός λογαριασμού.
 */
public final class IbanGenerator {

    public static final int TYPE_INDIVIDUAL = 1;
    public static final int TYPE_BUSINESS = 2;

    private static final String COUNTRY = "GR";
    private static final String BANK_CODE = "999";    // 3 ψηφία - βάλε τον κωδικό της δικής σου τράπεζας
    private static final String BRANCH_CODE = "0001"; // 4 ψηφία - κωδικός καταστήματος

    private IbanGenerator() { }

    /** Φτιάχνει IBAN από τον τύπο πελάτη και το μοναδικό id του στη βάση. */
    public static String generate(int customerType, long customerId) {
        // 16ψήφιος λογαριασμός: 2 ψηφία τύπου + 14 ψηφία id
        String account = String.format("%02d%014d", customerType, customerId);
        String bban = BANK_CODE + BRANCH_CODE + account;

        // Ψηφία ελέγχου (ISO 7064 mod 97-10): GR = 1627, + "00"
        int check = 98 - mod97(bban + "162700");
        return COUNTRY + String.format("%02d", check) + bban;
    }

    /** Έλεγχος εγκυρότητας IBAN. */
    public static boolean isValid(String iban) {
        if (iban == null || !iban.matches("GR\\d{25}")) {
            return false;
        }
        String rearranged = iban.substring(4) + "1627" + iban.substring(2, 4);
        return mod97(rearranged) == 1;
    }

    /** Μήκος του «αριθμού λογαριασμού»: τα 10 τελευταία ψηφία του IBAN. */
    public static final int ACCOUNT_NUMBER_LENGTH = 10;

    /** Αφαιρεί κενά και παύλες και κάνει κεφαλαία, για IBAN ή αριθμό λογαριασμού που πληκτρολόγησε χρήστης. */
    public static String cleanInput(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    /** Αριθμός λογαριασμού = 10 ψηφία (χωρίς γράμματα). */
    public static boolean isAccountNumber(String cleaned) {
        return cleaned != null && cleaned.matches("\\d{" + ACCOUNT_NUMBER_LENGTH + "}");
    }

    /** Ο αριθμός λογαριασμού (10 τελευταία ψηφία) ενός IBAN, ή «—» αν δεν υπάρχει IBAN. */
    public static String accountNumber(String iban) {
        if (iban == null) return "—";
        String s = iban.trim();
        return s.length() < ACCOUNT_NUMBER_LENGTH ? "—" : s.substring(s.length() - ACCOUNT_NUMBER_LENGTH);
    }

    /** Εμφάνιση σε ομάδες των 4: GR16 0110 1250 ... */
    public static String format(String iban) {
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static int mod97(String digits) {
        int mod = 0;
        for (int i = 0; i < digits.length(); i++) {
            mod = (mod * 10 + (digits.charAt(i) - '0')) % 97;
        }
        return mod;
    }
}
