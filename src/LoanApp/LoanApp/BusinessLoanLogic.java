package LoanApp.LoanApp;

    import java.util.Locale;

    /** Λογική αξιολόγησης αίτησης δανείου επιχείρησης. */
    public class BusinessLoanLogic {

        public static Main.Decision evaluate(BusinessApplication a) {
            CreditTier tier = CreditTier.fromScore(a.creditScore());
            BusinessScoreCalculator.BusinessFinancials f = a.fin();

            // 1. Πολύ κακός πιστοληπτικός βαθμός -> άμεση απόρριψη
            if (tier == CreditTier.REJECTED) {
                return Main.reject(tier, "Ο πιστοληπτικός βαθμός της επιχείρησης (" + a.creditScore()
                        + ") είναι κάτω από το ελάχιστο όριο (" + LoanAppParameters.MIN_SCORE_TO_APPLY + ").");
            }

            // 2. Αρνητικά / μηδενικά ίδια κεφάλαια -> απόρριψη
            if (f.equity() <= 0) {
                return Main.reject(tier, "Τα ίδια κεφάλαια της επιχείρησης είναι αρνητικά ή μηδενικά.");
            }

            // 3. Μέγιστο ποσό = ενεργητικό * όριο σκοπού * πολλαπλασιαστής ιστορικού * συντελεστής κατηγορίας ΕΛΠ
            double maxLoan = f.totalAssets() * a.purpose().maxLoanToAssets
                    * tier.limitMultiplier * a.category().limitFactor;
            if (a.amount() > maxLoan) {
                return Main.reject(tier, String.format(Locale.US,
                        "Το ποσό υπερβαίνει το μέγιστο επιτρεπτό (%.2f €) για σκοπό «%s», βαθμό «%s» και κατηγορία «%s».",
                        maxLoan, a.purpose().label, tier.label, a.category().label));
            }

            // 4. Επιτόκιο = βάση + spread βαθμού + πριμ σκοπού + spread κατηγορίας ΕΛΠ + προσαρμογή κάλυψης
            double coverage = f.totalAssets() / a.amount();
            double coverageAdj = coverage >= 3 ? -0.5 : coverage >= 1.5 ? 0.0 : 0.75;
            double rate = LoanAppParameters.BASE_RATE + tier.spread + a.purpose().ratePremium
                    + a.category().rateSpread + coverageAdj;

            // 5. Μηνιαία δόση (αποσβεστικό δάνειο)
            int months = a.termYears() * 12;
            double r = rate / 100 / 12;
            double payment = (r == 0) ? a.amount() / months
                    : a.amount() * r / (1 - Math.pow(1 + r, -months));
            double total = payment * months;

            // 6. Επάρκεια εισοδήματος: DSCR = EBITDA / (υφιστάμενες δόσεις + νέες δόσεις)
            double dscr = debtServiceCoverage(a, payment * 12);
            if (dscr < LoanAppParameters.MIN_DSCR) {
                return Main.reject(tier, String.format(Locale.US,
                        "Η κάλυψη εξυπηρέτησης χρέους (DSCR) είναι %.2f, κάτω από το ελάχιστο %.2f: "
                                + "τα EBITDA δεν επαρκούν για τις ετήσιες δόσεις.",
                        dscr, LoanAppParameters.MIN_DSCR));
            }

            return new Main.Decision(true, "Εγκρίθηκε", tier, rate, payment, total);
        }

        /** DSCR = EBITDA / (ετήσιες δόσεις υφιστάμενων δανείων + ετήσιες δόσεις νέου δανείου). */
        public static double debtServiceCoverage(BusinessApplication a, double newAnnualPayment) {
            double totalDebtService = a.fin().existingAnnualDebtService() + newAnnualPayment;
            return a.fin().ebitda() / totalDebtService;
        }
    }


