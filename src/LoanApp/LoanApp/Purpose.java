package LoanApp.LoanApp;

public enum Purpose {
    // ===================== ΣΚΟΠΟΣ ΔΑΝΕΙΟΥ =====================

        //            ονομασία          πριμ επιτοκίου %  max δάνειο ως % των assets
        INVESTMENT("Επένδυση", 1.0, 1.00),
        PERSONAL("Προσωπική χρήση", 0.5, 0.60),
        LEISURE("Αναψυχή", 2.5, 0.20);

        final String label;
        final double ratePremium;
        final double maxLoanToAssets;

        Purpose(String label, double ratePremium, double maxLoanToAssets) {
            this.label = label;
            this.ratePremium = ratePremium;
            this.maxLoanToAssets = maxLoanToAssets;
        }
    }

