package bank.capitalInvestment;

    /**
     * Υποθετικοί συντελεστές συσχέτισης μεταξύ των κατηγοριών (για τον υπολογισμό του κινδύνου χαρτοφυλακίου).
     * Είναι ενδεικτικοί: τα στεγαστικά, οι μετοχές και τα εταιρικά ομόλογα κινούνται μερικώς μαζί σε κρίση.
     */
    final class Correlations {
        private Correlations() {}

        static final double[][] MATRIX = build();

        private static double[][] build() {
            int n = AssetClass.values().length;
            double[][] m = new double[n][n];
            for (int i = 0; i < n; i++) m[i][i] = 1.0;

            AssetClass G = AssetClass.GOV_BONDS, CB = AssetClass.CORP_BONDS, E = AssetClass.EQUITIES;
            AssetClass M = AssetClass.MORTGAGES, C = AssetClass.CONSUMER, S = AssetClass.SME, K = AssetClass.CORPORATE;

            set(m, G, CB, 0.50); set(m, G, E, 0.10);
            for (AssetClass l : new AssetClass[]{M, C, S, K}) set(m, G, l, 0.15);
            set(m, CB, E, 0.60);
            for (AssetClass l : new AssetClass[]{M, C, S}) set(m, CB, l, 0.50);
            set(m, CB, K, 0.70);
            for (AssetClass l : new AssetClass[]{M, C, S, K}) set(m, E, l, 0.40);
            set(m, M, C, 0.50); set(m, M, S, 0.50); set(m, M, K, 0.50);
            set(m, C, S, 0.60); set(m, C, K, 0.50);
            set(m, S, K, 0.70);
            return m;   // τα μετρητά (CASH) δεν έχουν κίνδυνο αγοράς: η μεταβλητότητά τους είναι 0
        }

        private static void set(double[][] m, AssetClass a, AssetClass b, double v) {
            m[a.ordinal()][b.ordinal()] = v;
            m[b.ordinal()][a.ordinal()] = v;
        }
    }


