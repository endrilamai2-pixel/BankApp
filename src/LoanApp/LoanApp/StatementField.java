package LoanApp.LoanApp;


    public enum StatementField {
        // --- Πεδία που συμπληρώνονται στη φόρμα ---
        TOTAL_ASSETS("Σύνολο ενεργητικού", true),
        NET_TURNOVER("Καθαρός κύκλος εργασιών", true),
        AVG_EMPLOYEES("Μέσος όρος απασχολουμένων", true),
        EQUITY("Ίδια κεφάλαια", true),
        CURRENT_ASSETS("Κυκλοφορούν ενεργητικό", true),
        CURRENT_LIABILITIES("Βραχυπρόθεσμες υποχρεώσεις", true),
        EBITDA("EBITDA", true),
        NET_PROFIT("Καθαρά αποτελέσματα χρήσης", true),
        INTEREST_EXPENSE("Χρεωστικοί τόκοι", true),

        // --- Βοηθητικά (για τον υπολογισμό του EBITDA) ---
        EBIT("Αποτελέσματα προ τόκων και φόρων", false),
        PROFIT_BEFORE_TAX("Αποτέλεσμα προ φόρων", false),
        INTEREST_INCOME("Πιστωτικοί τόκοι", false),
        NET_INTEREST("Τόκοι και συναφή κονδύλια (καθαρό ποσό)", false),
        DEPRECIATION("Αποσβέσεις", false);

        public final String label;
        /** Αν το πεδίο συμπληρώνεται απευθείας στη φόρμα. */
        public final boolean target;

        StatementField(String label, boolean target) {
            this.label = label;
            this.target = target;
        }
    }


