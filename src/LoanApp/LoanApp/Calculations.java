package LoanApp.LoanApp;

import java.util.Locale;

public class Calculations {
    // ===================== ΚΥΡΙΑ ΛΟΓΙΚΗ =====================
    public static Main.Decision evaluate(Main.Application a) {
        CreditTier tier = CreditTier.fromScore(a.creditScore());

        // 1. Πολύ κακό ιστορικό -> άμεση απόρριψη
        if (tier == CreditTier.REJECTED) {
            return Main.reject(tier, "Το πιστοληπτικό ιστορικό (" +
                    a.creditScore() + ") είναι κάτω από το ελάχιστο όριο (" + LoanAppParameters.MIN_SCORE_TO_APPLY + ").");
        }

        // 2. Μέγιστο ποσό = assets * όριο σκοπού * πολλαπλασιαστής ιστορικού
        double maxLoan = a.assets() * a.purpose().maxLoanToAssets * tier.limitMultiplier;
        if (a.amount() > maxLoan) {
            return Main.reject(tier, String.format(Locale.US,
                    "Το ποσό υπερβαίνει το μέγιστο επιτρεπτό (%.2f €) για σκοπό «%s» και ιστορικό «%s».",
                    maxLoan, a.purpose().label, tier.label));
        }

        // 3. Επιτόκιο = βάση + spread ιστορικού + πριμ σκοπού + προσαρμογή κάλυψης assets
        double coverage = a.assets() / a.amount();        // πόσες φορές καλύπτεται το δάνειο
        double coverageAdj = coverage >= 3 ? -0.5 : coverage >= 1.5 ? 0.0 : 0.75;
        double rate = LoanAppParameters.BASE_RATE + tier.spread + a.purpose().ratePremium + coverageAdj;

        // 4. Μηνιαία δόση (αποσβεστικό δάνειο)
        int months = a.termYears() * 12;
        double r = rate / 100 / 12;
        double payment = (r == 0) ? a.amount() / months
                : a.amount() * r / (1 - Math.pow(1 + r, -months));
        double total = payment * months;

        return new Main.Decision(true, "Εγκρίθηκε", tier, rate, payment, total);
    }
}
