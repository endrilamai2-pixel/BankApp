package LoanApp.LoanApp;


    import java.util.Locale;

    /**
     * Κατηγορίες μεγέθους οντοτήτων σύμφωνα με το άρθρο 2 του Ν. 4308/2014 (Ελληνικά Λογιστικά Πρότυπα),
     * με τα όρια που ισχύουν μετά την τροποποίηση από το άρθρο 47 του Ν. 5164/2024.
     *
     * Μια οντότητα ανήκει στην κατηγορία της αν ΔΕΝ ΥΠΕΡΒΑΙΝΕΙ τα όρια σε τουλάχιστον 2 από τα 3 κριτήρια:
     * σύνολο ενεργητικού, καθαρός κύκλος εργασιών, μέσος όρος απασχολουμένων.
     *
     * Σημείωση: ο νόμος ορίζει ότι η αλλαγή κατηγορίας ενεργοποιείται μόνο όταν η υπέρβαση (ή η μη υπέρβαση)
     * επαναληφθεί σε δύο διαδοχικές περιόδους. Εδώ ελέγχεται μία χρήση (η τελευταία).
     */
    public enum EntityCategory {
        //        ονομασία       ενεργητικό   κύκλος εργασιών  προσωπικό  spread %  συντελεστής ορίου δανείου
        MICRO ("Πολύ μικρή",      450_000,       900_000,        10,       1.5,      0.60),
        SMALL ("Μικρή",         5_000_000,    10_000_000,        50,       1.0,      0.80),
        MEDIUM("Μεσαία",       25_000_000,    50_000_000,       250,       0.5,      0.90),
        LARGE ("Μεγάλη",   Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 1.00);

        private static final Locale GR = Locale.forLanguageTag("el-GR");

        public final String label;
        public final double maxAssets;
        public final double maxTurnover;
        public final double maxEmployees;
        /** Πριμ επιτοκίου % (οι μικρότερες οντότητες έχουν λιγότερο αξιόπιστα/απλούστερα στοιχεία). */
        public final double rateSpread;
        /** Πολλαπλασιαστής του μέγιστου επιτρεπτού δανείου. */
        public final double limitFactor;

        EntityCategory(String label, double maxAssets, double maxTurnover, double maxEmployees,
                       double rateSpread, double limitFactor) {
            this.label = label;
            this.maxAssets = maxAssets;
            this.maxTurnover = maxTurnover;
            this.maxEmployees = maxEmployees;
            this.rateSpread = rateSpread;
            this.limitFactor = limitFactor;
        }

        /** Κατατάσσει την οντότητα: ελέγχει πρώτα την πολύ μικρή, μετά τη μικρή, μετά τη μεσαία. */
        public static EntityCategory classify(double totalAssets, double netTurnover, double avgEmployees) {
            for (EntityCategory c : new EntityCategory[]{MICRO, SMALL, MEDIUM}) {
                if (c.criteriaWithinLimit(totalAssets, netTurnover, avgEmployees) >= 2) return c;
            }
            return LARGE;
        }

        /** Πόσα από τα 3 κριτήρια ΔΕΝ υπερβαίνουν τα όρια της κατηγορίας. */
        int criteriaWithinLimit(double totalAssets, double netTurnover, double avgEmployees) {
            int n = 0;
            if (totalAssets <= maxAssets) n++;
            if (netTurnover <= maxTurnover) n++;
            if (avgEmployees <= maxEmployees) n++;
            return n;
        }

        /** Περιγραφή των ορίων της κατηγορίας, για εμφάνιση. */
        public String criteriaText() {
            if (this == LARGE) {
                return "Υπερβαίνει τα όρια της μεσαίας σε τουλάχιστον 2 από τα 3 κριτήρια";
            }
            return String.format(GR, "Ενεργητικό ≤ %,.0f €  |  Κύκλος εργασιών ≤ %,.0f €  |  Προσωπικό ≤ %.0f",
                    maxAssets, maxTurnover, maxEmployees);
        }
    }


