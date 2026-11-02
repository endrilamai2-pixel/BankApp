package LoanApp.LoanApp;

public enum CreditTier {
    // ===================== ΚΑΤΗΓΟΡΙΑ ΠΙΣΤΟΛΗΠΤΙΚΟΥ ΙΣΤΟΡΙΚΟΥ =====================

        //                    ονομασία        spread %  πολλαπλασιαστής ορίου
        EXCELLENT("Εξαιρετικό", 0.0, 1.00),
        GOOD("Καλό", 1.0, 0.85),
        FAIR("Μέτριο", 2.5, 0.60),
        POOR("Κακό", 5.0, 0.35),
        REJECTED("Πολύ κακό", 0.0, 0.00);

        final String label;
        final double spread;
        final double limitMultiplier;

        CreditTier(String label, double spread, double limitMultiplier) {
            this.label = label;
            this.spread = spread;
            this.limitMultiplier = limitMultiplier;
        }

        static CreditTier fromScore(int score) {
            if (score < LoanAppParameters.MIN_SCORE_TO_APPLY) return REJECTED;
            if (score < 580) return POOR;
            if (score < 670) return FAIR;
            if (score < 740) return GOOD;
            return EXCELLENT;
        }
    }

