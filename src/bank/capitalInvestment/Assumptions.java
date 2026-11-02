package bank.capitalInvestment;


    import java.util.ArrayList;
import java.util.List;

    /**
     * Όλες οι παράμετροι του μοντέλου. Τα ποσά είναι σε ευρώ και τα ποσοστά σε % (π.χ. 10,5 σημαίνει 10,5%).
     *
     * Ρυθμιστικές παράμετροι (προεπιλογές):
     *  - Ελάχιστα αποθεματικά ΕΚΤ: 1% των καταθέσεων (ο δείκτης είναι 1% από τον Ιανουάριο του 2012, και τα ελάχιστα
     *    αποθεματικά αμείβονται με 0%). Τα πλεονάζοντα αποθεματικά αμείβονται με το επιτόκιο αποθετηρίου.
     *  - LCR: τουλάχιστον 100% (ρευστά διαθέσιμα υψηλής ποιότητας προς καθαρές εκροές 30 ημερών).
     *  - Κεφαλαιακή επάρκεια: 8% (Πυλώνας 1) + 2,5% (απόθεμα διατήρησης κεφαλαίου) = 10,5%. Στην πράξη προστίθενται
     *    απαιτήσεις του Πυλώνα 2 και άλλα αποθέματα που ορίζει ο εποπτικός φορέας ανά τράπεζα.
     *  - Δείκτης μόχλευσης: τουλάχιστον 3%.
     */
    public final class Assumptions {
        private static final int N = AssetClass.values().length;

        // ----- Μέγεθος και χρηματοδότηση -----
        public double funds = 1_000_000_000;        // συνολικά κεφάλαια προς τοποθέτηση (ενεργητικό)
        public double ownFunds = 100_000_000;       // ίδια κεφάλαια (εποπτικά)
        public double depositCost = 0.50;           // κόστος χρηματοδότησης καταθέσεων, % ετησίως
        public double overhead = 1.20;              // γενικά λειτουργικά έξοδα, % του ενεργητικού
        public double taxRate = 22.0;               // φορολογικός συντελεστής, %

        // ----- Ρυθμιστικοί περιορισμοί -----
        public double reserveRatio = 1.0;           // ελάχιστα αποθεματικά ΕΚΤ, % των καταθέσεων
        public double outflowRate = 10.0;           // υποθετική εκροή καταθέσεων 30 ημερών (για LCR), %
        public double minLcr = 100.0;               // ελάχιστος LCR, %
        public double capitalRatio = 10.5;          // ελάχιστος δείκτης συνολικών ιδίων κεφαλαίων, % των σταθμισμένων στοιχείων
        public double leverageRatio = 3.0;          // ελάχιστος δείκτης μόχλευσης, %

        // ----- Ανοχή κινδύνου -----
        public double varLimit = 75.0;              // ανώτατη ζημιά 99% σε ένα έτος, ως % των ιδίων κεφαλαίων

        // ----- Παράμετροι ανά κατηγορία (ευρετήριο = AssetClass.ordinal()) -----
        public final double[] expectedReturn = new double[N];   // %
        public final double[] expectedLoss = new double[N];     // % (ετήσια αναμενόμενη ζημιά)
        public final double[] opCost = new double[N];           // % (κόστος διαχείρισης/εξυπηρέτησης)
        public final double[] volatility = new double[N];       // % (ετήσια τυπική απόκλιση)
        public final double[] riskWeight = new double[N];       // % (συντελεστής στάθμισης κινδύνου)
        public final double[] maxShare = new double[N];         // % του συνόλου των κεφαλαίων

        public static Assumptions defaults() {
            Assumptions a = new Assumptions();
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                a.expectedReturn[i] = c.defaultReturn;
                a.expectedLoss[i] = c.defaultLoss;
                a.opCost[i] = c.defaultOpCost;
                a.volatility[i] = c.defaultVolatility;
                a.riskWeight[i] = c.defaultRiskWeight;
                a.maxShare[i] = c.defaultMaxShare;
            }
            return a;
        }

        public Assumptions copy() {
            Assumptions a = new Assumptions();
            a.funds = funds; a.ownFunds = ownFunds; a.depositCost = depositCost; a.overhead = overhead; a.taxRate = taxRate;
            a.reserveRatio = reserveRatio; a.outflowRate = outflowRate; a.minLcr = minLcr;
            a.capitalRatio = capitalRatio; a.leverageRatio = leverageRatio; a.varLimit = varLimit;
            System.arraycopy(expectedReturn, 0, a.expectedReturn, 0, N);
            System.arraycopy(expectedLoss, 0, a.expectedLoss, 0, N);
            System.arraycopy(opCost, 0, a.opCost, 0, N);
            System.arraycopy(volatility, 0, a.volatility, 0, N);
            System.arraycopy(riskWeight, 0, a.riskWeight, 0, N);
            System.arraycopy(maxShare, 0, a.maxShare, 0, N);
            return a;
        }

        /** Καταθέσεις = σύνολο κεφαλαίων μείον ίδια κεφάλαια. */
        public double deposits() {
            return funds - ownFunds;
        }

        /** Ελάχιστο ποσό που πρέπει να κρατά η τράπεζα στην κεντρική τράπεζα (ελάχιστα αποθεματικά ΕΚΤ). */
        public double minReserveAmount() {
            return reserveRatio / 100.0 * deposits();
        }

        /** Ελάχιστα υψηλής ποιότητας ρευστά διαθέσιμα (HQLA) για να ισχύει ο ελάχιστος LCR. */
        public double minHqlaAmount() {
            return minLcr / 100.0 * outflowRate / 100.0 * deposits();
        }

        /** Μέγιστα επιτρεπτά σταθμισμένα στοιχεία ενεργητικού με τα ίδια κεφάλαια που υπάρχουν. */
        public double maxRwa() {
            return ownFunds / (capitalRatio / 100.0);
        }

        /** Έλεγχος εισόδου: επιστρέφει τα προβλήματα (κενή λίστα = όλα έγκυρα). */
        public List<String> validate() {
            List<String> p = new ArrayList<>();
            if (!(funds > 0)) p.add("Τα κεφάλαια προς τοποθέτηση πρέπει να είναι θετικά.");
            if (!(ownFunds > 0)) p.add("Τα ίδια κεφάλαια πρέπει να είναι θετικά.");
            if (ownFunds >= funds) p.add("Τα ίδια κεφάλαια πρέπει να είναι λιγότερα από το σύνολο των κεφαλαίων (η διαφορά είναι οι καταθέσεις).");
            pct(p, "Κόστος καταθέσεων", depositCost, 0, 20);
            pct(p, "Γενικά λειτουργικά έξοδα", overhead, 0, 20);
            pct(p, "Φορολογικός συντελεστής", taxRate, 0, 100);
            pct(p, "Ελάχιστα αποθεματικά", reserveRatio, 0, 100);
            pct(p, "Εκροή καταθέσεων", outflowRate, 0, 100);
            pct(p, "Ελάχιστος LCR", minLcr, 0, 1000);
            pct(p, "Δείκτης κεφαλαιακής επάρκειας", capitalRatio, 0.1, 100);
            pct(p, "Δείκτης μόχλευσης", leverageRatio, 0, 100);
            pct(p, "Όριο κινδύνου (VaR)", varLimit, 1, 1000);
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                pct(p, c.label + ": απόδοση", expectedReturn[i], -50, 100);
                pct(p, c.label + ": αναμενόμενη ζημιά", expectedLoss[i], 0, 100);
                pct(p, c.label + ": λειτουργικό κόστος", opCost[i], 0, 100);
                pct(p, c.label + ": μεταβλητότητα", volatility[i], 0, 200);
                pct(p, c.label + ": συντελεστής κινδύνου", riskWeight[i], 0, 1250);
                pct(p, c.label + ": μέγιστο ποσοστό", maxShare[i], 0, 100);
            }
            return p;
        }

        private static void pct(List<String> problems, String what, double v, double min, double max) {
            if (Double.isNaN(v) || v < min || v > max) {
                problems.add(what + ": η τιμή πρέπει να είναι από " + min + " έως " + max + ".");
            }
        }
    }


