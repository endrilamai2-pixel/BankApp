package LoanApp.LoanApp;

/**
 * Πιστοληπτική βαθμολόγηση επιχείρησης (κλίμακα 300-850, όπως για τα φυσικά πρόσωπα),
 * με βάση χρηματοοικονομικούς δείκτες από τις οικονομικές καταστάσεις (ΕΛΠ).
 *
 *   30% Συμπεριφορά πληρωμών | 25% Φερεγγυότητα (ίδια κεφάλαια / ενεργητικό)
 *   15% Ρευστότητα (κυκλοφορούν / βραχυπρόθεσμες υποχρεώσεις) | 15% Κερδοφορία (καθαρό περιθώριο)
 *   10% Κάλυψη τόκων (EBITDA / χρεωστικοί τόκοι) | 5% Ηλικία επιχείρησης
 */
public class BusinessScoreCalculator {

    static final double W_PAYMENT       = 0.30;
    static final double W_SOLVENCY      = 0.25;
    static final double W_LIQUIDITY     = 0.15;
    static final double W_PROFITABILITY = 0.15;
    static final double W_INTEREST      = 0.10;
    static final double W_AGE           = 0.05;

    /** Στοιχεία της επιχείρησης (από ισολογισμό / κατάσταση αποτελεσμάτων ΕΛΠ και ιστορικό συναλλαγών). */
    public record BusinessFinancials(
            double totalAssets,             // Σύνολο ενεργητικού (€)
            double netTurnover,             // Καθαρός κύκλος εργασιών (€)
            double avgEmployees,            // Μέσος όρος απασχολουμένων
            double equity,                  // Ίδια κεφάλαια (€), μπορεί να είναι αρνητικά
            double currentAssets,           // Κυκλοφορούν ενεργητικό (€)
            double currentLiabilities,      // Βραχυπρόθεσμες υποχρεώσεις (€)
            double ebitda,                  // Κέρδη προ τόκων, φόρων, αποσβέσεων (€)
            double netProfit,               // Καθαρά αποτελέσματα χρήσης (€), μπορεί να είναι ζημία
            double interestExpense,         // Χρεωστικοί τόκοι (€)
            double existingAnnualDebtService, // Ετήσιες δόσεις υφιστάμενων δανείων (€)
            double yearsInOperation,        // Έτη λειτουργίας
            int latePayments,               // Καθυστερήσεις πληρωμών 30+ ημερών (2 έτη)
            int bouncedCheques,             // Ακάλυπτες επιταγές / απλήρωτες συναλλαγματικές
            boolean taxOrSocialSecurityArrears, // Ληξιπρόθεσμα προς ΑΑΔΕ / ΕΦΚΑ
            boolean insolvencyProceedings   // Πτώχευση, εξυγίανση, συνδιαλλαγή κ.λπ.
    ) {
        public BusinessFinancials {
            if (totalAssets <= 0)
                throw new IllegalArgumentException("Το σύνολο ενεργητικού πρέπει να είναι μεγαλύτερο από 0.");
            if (netTurnover < 0 || avgEmployees < 0 || currentAssets < 0 || currentLiabilities < 0
                    || interestExpense < 0 || existingAnnualDebtService < 0 || yearsInOperation < 0
                    || latePayments < 0 || bouncedCheques < 0)
                throw new IllegalArgumentException("Οι τιμές (εκτός από ίδια κεφάλαια, EBITDA και αποτελέσματα) δεν μπορούν να είναι αρνητικές.");
        }
    }

    /** Αποτέλεσμα με ανάλυση ανά παράγοντα (0-100) και τους δείκτες που υπολογίστηκαν. */
    public record BusinessScoreResult(int score,
                                      double payment, double solvency, double liquidity,
                                      double profitability, double interest, double age,
                                      boolean cappedByInsolvency,
                                      double equityRatio, double currentRatio,
                                      double netMargin, double interestCoverage) {}

    public static BusinessScoreResult calculate(BusinessFinancials f) {
        double equityRatio = f.equity() / f.totalAssets();
        // Οι πολύ μικρές οντότητες (Υπόδειγμα Β.5 του Ν. 4308/2014) δεν παρουσιάζουν κυκλοφορούν ενεργητικό.
        // Μηδενικό κυκλοφορούν θεωρείται «δεν δόθηκε» και βαθμολογείται ουδέτερα αντί να τιμωρεί την επιχείρηση.
        double currentRatio = f.currentAssets() == 0 ? Double.NaN
                : f.currentLiabilities() == 0 ? 2.0 : f.currentAssets() / f.currentLiabilities();
        double netMargin = f.netTurnover() == 0 ? 0.0 : f.netProfit() / f.netTurnover();
        double interestCoverage = f.interestExpense() == 0 ? Double.NaN : f.ebitda() / f.interestExpense();

        double pay  = paymentScore(f);
        double sol  = solvencyScore(equityRatio);
        double liq  = Double.isNaN(currentRatio) ? 0.5 : liquidityScore(currentRatio);
        double prof = profitabilityScore(netMargin);
        double intr = interestScore(interestCoverage);
        double age  = clamp(f.yearsInOperation() / 5.0);

        double weighted = pay * W_PAYMENT + sol * W_SOLVENCY + liq * W_LIQUIDITY
                + prof * W_PROFITABILITY + intr * W_INTEREST + age * W_AGE;

        int score = (int) Math.round(CreditScoreCalculator.MIN_SCORE
                + weighted * (CreditScoreCalculator.MAX_SCORE - CreditScoreCalculator.MIN_SCORE));

        boolean capped = false;
        if (f.insolvencyProceedings() && score > CreditScoreCalculator.SCORE_CAP_ON_DEFAULT) {
            score = CreditScoreCalculator.SCORE_CAP_ON_DEFAULT;
            capped = true;
        }
        score = Math.max(CreditScoreCalculator.MIN_SCORE, Math.min(CreditScoreCalculator.MAX_SCORE, score));

        return new BusinessScoreResult(score, pay * 100, sol * 100, liq * 100, prof * 100,
                intr * 100, age * 100, capped, equityRatio, currentRatio, netMargin, interestCoverage);
    }

    // ===================== ΥΠΟΛΟΓΙΣΜΟΣ ΠΑΡΑΓΟΝΤΩΝ (0.0 - 1.0) =====================

    static double paymentScore(BusinessFinancials f) {
        double s = 1.0 - f.latePayments() * 0.07 - f.bouncedCheques() * 0.20
                - (f.taxOrSocialSecurityArrears() ? 0.35 : 0.0);
        return clamp(s);
    }

    static double solvencyScore(double equityRatio) {
        if (equityRatio >= 0.50) return 1.00;
        if (equityRatio >= 0.30) return 0.80;
        if (equityRatio >= 0.20) return 0.60;
        if (equityRatio >= 0.10) return 0.35;
        if (equityRatio > 0)     return 0.15;
        return 0.0;                              // αρνητικά ή μηδενικά ίδια κεφάλαια
    }

    static double liquidityScore(double currentRatio) {
        if (currentRatio >= 2.0) return 1.00;
        if (currentRatio >= 1.5) return 0.85;
        if (currentRatio >= 1.2) return 0.65;
        if (currentRatio >= 1.0) return 0.45;
        if (currentRatio >= 0.8) return 0.20;
        return 0.0;
    }

    static double profitabilityScore(double margin) {
        if (margin >= 0.10) return 1.00;
        if (margin >= 0.05) return 0.80;
        if (margin >= 0.02) return 0.60;
        if (margin > 0)     return 0.40;
        if (margin == 0)    return 0.25;
        if (margin >= -0.05) return 0.15;
        return 0.0;
    }

    static double interestScore(double coverage) {
        if (Double.isNaN(coverage)) return 0.85; // χωρίς τόκους: καμία επιβάρυνση
        if (coverage >= 8) return 1.00;
        if (coverage >= 4) return 0.80;
        if (coverage >= 2) return 0.50;
        if (coverage >= 1.2) return 0.25;
        return 0.0;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
