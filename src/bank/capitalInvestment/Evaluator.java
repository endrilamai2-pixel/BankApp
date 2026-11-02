package bank.capitalInvestment;


    /**
     * Υπολογίζει κέρδος, κίνδυνο και ρυθμιστικούς δείκτες για μια κατανομή (ποσοστά w[i] που αθροίζουν στο 1).
     * Οι προϋπολογισμένοι πίνακες κάνουν την αξιολόγηση αρκετά γρήγορη για εκατοντάδες χιλιάδες συνδυασμούς.
     */
    final class Evaluator {
        static final double Z_99 = 2.326;      // 99ο εκατοστημόριο της κανονικής κατανομής
        static final double EPS = 1e-9;

        private final Assumptions a;
        private final int n = AssetClass.values().length;
        private final double funds, ownFunds, deposits;
        private final double[] netReturn;      // (απόδοση - ζημιά - κόστος), ως κλάσμα
        private final double[] riskWeight;     // κλάσμα
        private final double[] cap;            // κλάσμα
        private final double[][] cov;
        private final boolean[] hqla = new boolean[n];
        private final double minReserve, hqlaNeeded, maxRwa;
        private final double fixedCosts;       // κόστος καταθέσεων + γενικά έξοδα (€)
        private final int cashIdx = AssetClass.CASH.ordinal();

        Evaluator(Assumptions a) {
            this.a = a;
            funds = a.funds;
            ownFunds = a.ownFunds;
            deposits = a.deposits();
            netReturn = new double[n];
            riskWeight = new double[n];
            cap = new double[n];
            double[] vol = new double[n];
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                netReturn[i] = (a.expectedReturn[i] - a.expectedLoss[i] - a.opCost[i]) / 100.0;
                riskWeight[i] = a.riskWeight[i] / 100.0;
                cap[i] = a.maxShare[i] / 100.0;
                vol[i] = a.volatility[i] / 100.0;
                hqla[i] = c.hqla;
            }
            cov = new double[n][n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) cov[i][j] = Correlations.MATRIX[i][j] * vol[i] * vol[j];

            minReserve = a.reserveRatio / 100.0 * deposits;
            hqlaNeeded = a.minLcr / 100.0 * a.outflowRate / 100.0 * deposits;
            maxRwa = ownFunds / (a.capitalRatio / 100.0);
            fixedCosts = deposits * a.depositCost / 100.0 + funds * a.overhead / 100.0;
        }

        double cap(int i) { return cap[i]; }

        /** Κέρδος προ φόρων: τα ελάχιστα αποθεματικά αμείβονται με 0%, μόνο τα πλεονάζοντα με το επιτόκιο αποθετηρίου. */
        double profitBeforeTax(double[] w) {
            double income = 0;
            for (int i = 0; i < n; i++) {
                if (i == cashIdx) {
                    income += Math.max(0, w[i] * funds - minReserve) * netReturn[i];
                } else {
                    income += w[i] * funds * netReturn[i];
                }
            }
            return income - fixedCosts;
        }

        double netProfit(double[] w) {
            double pbt = profitBeforeTax(w);
            return pbt - Math.max(0, pbt) * a.taxRate / 100.0;
        }

        double rwa(double[] w) {
            double s = 0;
            for (int i = 0; i < n; i++) s += w[i] * funds * riskWeight[i];
            return s;
        }

        double sigma(double[] w) {
            double s2 = 0;
            for (int i = 0; i < n; i++) {
                if (w[i] == 0) continue;
                for (int j = 0; j < n; j++) s2 += w[i] * w[j] * cov[i][j];
            }
            return Math.sqrt(Math.max(0, s2));
        }

        double hqla(double[] w) {
            double s = 0;
            for (int i = 0; i < n; i++) if (hqla[i]) s += w[i] * funds;
            return s;
        }

        /** Ισχύουν ΟΛΟΙ οι περιορισμοί; (η κατανομή θεωρείται ήδη μη αρνητική με άθροισμα 1) */
        boolean feasible(double[] w) {
            for (int i = 0; i < n; i++) if (w[i] > cap[i] + EPS) return false;
            if (w[cashIdx] * funds < minReserve - EPS) return false;                 // ελάχιστα αποθεματικά ΕΚΤ
            if (hqla(w) < hqlaNeeded - EPS) return false;                            // LCR
            if (rwa(w) > maxRwa + EPS) return false;                                 // κεφαλαιακή επάρκεια
            if (Z_99 * sigma(w) * funds > a.varLimit / 100.0 * ownFunds + EPS) return false;   // όριο κινδύνου
            return ownFunds / funds >= a.leverageRatio / 100.0 - EPS;                // δείκτης μόχλευσης
        }

        Metrics metrics(double[] w) {
            double[] amounts = new double[n];
            for (int i = 0; i < n; i++) amounts[i] = w[i] * funds;
            double pbt = profitBeforeTax(w);
            double net = pbt - Math.max(0, pbt) * a.taxRate / 100.0;
            double rwa = rwa(w);
            double sig = sigma(w);
            double var = Z_99 * sig * funds;
            double lcr = hqlaNeeded > 0 ? hqla(w) / (a.outflowRate / 100.0 * deposits) * 100.0 : Double.POSITIVE_INFINITY;
            return new Metrics(amounts, pbt, net, net / ownFunds * 100.0, rwa, maxRwa,
                    rwa > 0 ? ownFunds / rwa * 100.0 : Double.POSITIVE_INFINITY,
                    ownFunds / funds * 100.0, lcr, amounts[cashIdx], minReserve,
                    sig * 100.0, var, var / ownFunds * 100.0);
        }
    }


