package LoanApp.LoanApp;


    // ===================== ΣΚΟΠΟΣ ΕΠΙΧΕΙΡΗΜΑΤΙΚΟΥ ΔΑΝΕΙΟΥ =====================
    public enum BusinessPurpose {
        //                  ονομασία                         πριμ επιτοκίου %   max δάνειο ως % του ενεργητικού
        INVESTMENT("Επένδυση / επέκταση (πάγια)",              0.75,             0.60),
        REFINANCING("Αναχρηματοδότηση υφιστάμενων δανείων",    0.50,             0.45),
        WORKING_CAPITAL("Κεφάλαιο κίνησης",                    1.25,             0.35);

        public final String label;
        public final double ratePremium;
        public final double maxLoanToAssets;

        BusinessPurpose(String label, double ratePremium, double maxLoanToAssets) {
            this.label = label;
            this.ratePremium = ratePremium;
            this.maxLoanToAssets = maxLoanToAssets;
        }
    }


