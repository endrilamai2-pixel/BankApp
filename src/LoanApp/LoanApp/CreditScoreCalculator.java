package LoanApp.LoanApp;

public class CreditScoreCalculator {
    /**
     * Αυτόματος υπολογισμός πιστοληπτικού σκορ (κλίμακα 300-850).
     * Βασίζεται σε μοντέλο τύπου FICO με πέντε παράγοντες:
     *   35% Ιστορικό πληρωμών | 30% Χρήση πίστωσης | 15% Διάρκεια ιστορικού
     *   10% Πρόσφατα αιτήματα πίστωσης | 10% Ποικιλία πιστωτικών προϊόντων
     */


        // ===================== ΒΑΡΗ ΠΑΡΑΓΟΝΤΩΝ (άθροισμα = 1.0) =====================
        static final double W_PAYMENT_HISTORY = 0.35;
        static final double W_UTILIZATION     = 0.30;
        static final double W_HISTORY_LENGTH  = 0.15;
        static final double W_INQUIRIES       = 0.10;
        static final double W_CREDIT_MIX      = 0.10;

        static final int MIN_SCORE = 300;
        static final int MAX_SCORE = 850;
        /** Ανώτατο σκορ αν υπάρχει πτώχευση ή αθέτηση δανείου. */
        static final int SCORE_CAP_ON_DEFAULT = 480;

        /** Τα στοιχεία του αιτούντος που χρειάζεται ο υπολογισμός. */
        public record CreditProfile(
                int late30Days,          // καθυστερήσεις 30+ ημερών (τελευταία 2 έτη)
                int late60Days,          // καθυστερήσεις 60+ ημερών
                int late90PlusDays,      // καθυστερήσεις 90+ ημερών
                boolean hasDefaultOrBankruptcy,
                double totalCreditLimit, // συνολικό όριο καρτών/πιστωτικών γραμμών (€)
                double totalCreditUsed,  // συνολικό χρησιμοποιημένο υπόλοιπο (€)
                double historyYears,     // πόσα χρόνια υπάρχει πιστωτικό ιστορικό
                int recentInquiries,     // αιτήσεις πίστωσης τους τελευταίους 12 μήνες
                int accountTypes         // είδη πίστωσης (κάρτα, στεγαστικό, καταναλωτικό, ...)
        ) {
            public CreditProfile {
                if (late30Days < 0 || late60Days < 0 || late90PlusDays < 0
                        || totalCreditLimit < 0 || totalCreditUsed < 0
                        || historyYears < 0 || recentInquiries < 0 || accountTypes < 0) {
                    throw new IllegalArgumentException("Οι τιμές δεν μπορούν να είναι αρνητικές.");
                }
            }

            /** Ποσοστό χρήσης πίστωσης (0-1+). -1 αν δεν υπάρχει όριο. */
            double utilization() {
                return totalCreditLimit == 0 ? -1 : totalCreditUsed / totalCreditLimit;
            }
        }

        /** Αποτέλεσμα με ανάλυση ανά παράγοντα (0-100 ο καθένας). */
        public record ScoreResult(int score,
                                  double paymentHistory, double utilization,
                                  double historyLength, double inquiries, double creditMix,
                                  boolean cappedByDefault) {}

        // ===================== ΚΥΡΙΑ ΜΕΘΟΔΟΣ =====================
        public static ScoreResult calculate(CreditProfile p) {
            double pay  = paymentHistoryScore(p);
            double util = utilizationScore(p);
            double hist = historyLengthScore(p);
            double inq  = inquiriesScore(p);
            double mix  = creditMixScore(p);

            double weighted = pay * W_PAYMENT_HISTORY
                    + util * W_UTILIZATION
                    + hist * W_HISTORY_LENGTH
                    + inq * W_INQUIRIES
                    + mix * W_CREDIT_MIX;

            int score = (int) Math.round(MIN_SCORE + weighted * (MAX_SCORE - MIN_SCORE));

            boolean capped = false;
            if (p.hasDefaultOrBankruptcy() && score > SCORE_CAP_ON_DEFAULT) {
                score = SCORE_CAP_ON_DEFAULT;
                capped = true;
            }
            score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));

            return new ScoreResult(score, pay * 100, util * 100, hist * 100, inq * 100, mix * 100, capped);
        }

        // ===================== ΥΠΟΛΟΓΙΣΜΟΣ ΚΑΘΕ ΠΑΡΑΓΟΝΤΑ (0.0 - 1.0) =====================

        /** Κάθε καθυστέρηση αφαιρεί βαθμούς· οι πιο σοβαρές αφαιρούν περισσότερο. */
        static double paymentHistoryScore(CreditProfile p) {
            double s = 1.0
                    - p.late30Days() * 0.08
                    - p.late60Days() * 0.15
                    - p.late90PlusDays() * 0.30;
            return clamp(s);
        }

        /** Όσο χαμηλότερη η χρήση του διαθέσιμου ορίου, τόσο καλύτερα. */
        static double utilizationScore(CreditProfile p) {
            double u = p.utilization();
            if (u < 0) return 0.5;          // καθόλου πιστωτικό όριο: ουδέτερο
            if (u <= 0.10) return 1.00;
            if (u <= 0.30) return 0.85;
            if (u <= 0.50) return 0.60;
            if (u <= 0.75) return 0.30;
            if (u <= 1.00) return 0.10;
            return 0.0;                     // πάνω από το όριο
        }

        /** Πλήρης βαθμολογία στα 10 χρόνια ιστορικού. */
        static double historyLengthScore(CreditProfile p) {
            return clamp(p.historyYears() / 10.0);
        }

        /** Πολλά πρόσφατα αιτήματα πίστωσης θεωρούνται ένδειξη κινδύνου. */
        static double inquiriesScore(CreditProfile p) {
            return switch (p.recentInquiries()) {
                case 0 -> 1.00;
                case 1 -> 0.90;
                case 2 -> 0.75;
                case 3 -> 0.60;
                case 4, 5 -> 0.40;
                default -> 0.20;
            };
        }

        /** Ποικιλία πιστωτικών προϊόντων (κάρτα, στεγαστικό, καταναλωτικό κ.λπ.). */
        static double creditMixScore(CreditProfile p) {
            return switch (p.accountTypes()) {
                case 0 -> 0.30;
                case 1 -> 0.40;
                case 2 -> 0.65;
                case 3 -> 0.85;
                default -> 1.00;
            };
        }

        private static double clamp(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }
    }

