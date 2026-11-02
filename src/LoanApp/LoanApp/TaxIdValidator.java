package LoanApp.LoanApp;


    /** Έλεγχος εγκυρότητας ελληνικού ΑΦΜ (9 ψηφία + ψηφίο ελέγχου), πριν γίνει οποιαδήποτε κλήση στο μητρώο. */
    public final class TaxIdValidator {
        private TaxIdValidator() {}

        public static boolean isValid(String taxId) {
            if (taxId == null || !taxId.matches("\\d{9}") || taxId.equals("000000000")) return false;
            int sum = 0;
            for (int i = 0; i < 8; i++) {
                sum += (taxId.charAt(i) - '0') << (8 - i);   // ψηφίο * 2^(8-i)
            }
            return (sum % 11) % 10 == taxId.charAt(8) - '0';
        }
    }


