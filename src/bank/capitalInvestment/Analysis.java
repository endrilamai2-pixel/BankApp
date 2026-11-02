package bank.capitalInvestment;

    import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

    /** Εκτελεί όλους τους υπολογισμούς: συγκρίνει τις κινήσεις και εξηγεί τι περιορίζει την απόδοση. */
    public final class Analysis {
        private Analysis() {}

        private static final Locale GR = Locale.forLanguageTag("el-GR");
        /** Πλέγμα για την ανάλυση ευαισθησίας (γρήγορο και σταθερό ώστε οι συγκρίσεις να είναι δίκαιες). */
        private static final int SENSITIVITY_STEP = 5;

        /** Ένας περιορισμός που δεσμεύει, και πόσο θα κέρδιζε η τράπεζα αν χαλάρωνε. */
        public record Insight(String text, double gain) {}

        public record Report(Map<Strategy, Optional<PortfolioOptimizer.Solution>> results,
                             List<Insight> insights,
                             List<String> problems) {

            public boolean hasResults() {
                return results.values().stream().anyMatch(Optional::isPresent);
            }

            /** Η εφικτή κίνηση με το μεγαλύτερο καθαρό κέρδος. */
            public Optional<Strategy> best() {
                Strategy best = null;
                double bestProfit = Double.NEGATIVE_INFINITY;
                for (Strategy s : Strategy.values()) {
                    Optional<PortfolioOptimizer.Solution> r = results.get(s);
                    if (r != null && r.isPresent() && r.get().metrics().netProfit() > bestProfit + 1e-6) {
                        bestProfit = r.get().metrics().netProfit();
                        best = s;
                    }
                }
                return Optional.ofNullable(best);
            }
        }

        public static Report run(Assumptions a, int stepPercent) {
            Map<Strategy, Optional<PortfolioOptimizer.Solution>> results = new EnumMap<>(Strategy.class);
            List<String> problems = a.validate();
            if (!problems.isEmpty()) return new Report(results, List.of(), problems);

            for (Strategy s : Strategy.values()) {
                results.put(s, PortfolioOptimizer.optimize(a, s.allowedClasses(), stepPercent));
            }
            List<Insight> insights = new ArrayList<>();

            Optional<PortfolioOptimizer.Solution> all = results.get(Strategy.ALL);
            if (all.isEmpty()) {
                problems.addAll(diagnoseInfeasible(a));
            } else {
                insights.addAll(sensitivity(a, all.get()));
            }
            return new Report(results, insights, problems);
        }

        // ===================== ΓΙΑΤΙ ΔΕΝ ΥΠΑΡΧΕΙ ΛΥΣΗ =====================

        static List<String> diagnoseInfeasible(Assumptions a) {
            List<String> why = new ArrayList<>();
            if (a.ownFunds / a.funds * 100 < a.leverageRatio) {
                why.add(String.format(GR, "Ο δείκτης μόχλευσης είναι %.2f%% και πρέπει να είναι τουλάχιστον %.2f%%: "
                        + "τα ίδια κεφάλαια είναι πολύ λίγα σε σχέση με το ενεργητικό.", a.ownFunds / a.funds * 100, a.leverageRatio));
            }
            double capSum = 0;
            for (double c : a.maxShare) capSum += c;
            if (capSum < 100) why.add("Τα όρια ανά κατηγορία αθροίζουν λιγότερο από 100%: δεν υπάρχει πού να τοποθετηθούν όλα τα κεφάλαια.");
            if (a.minHqlaAmount() > a.funds) why.add("Τα απαιτούμενα ρευστά διαθέσιμα (LCR) ξεπερνούν το σύνολο των κεφαλαίων.");
            if (why.isEmpty()) {
                why.add("Δεν υπάρχει κατανομή που να ικανοποιεί ταυτόχρονα όλους τους περιορισμούς (ρευστότητα, κεφαλαιακή "
                        + "επάρκεια, όριο κινδύνου, όρια ανά κατηγορία). Χαλαρώστε το όριο κινδύνου ή αυξήστε τα ίδια κεφάλαια.");
            }
            return why;
        }

        // ===================== ΤΙ ΠΕΡΙΟΡΙΖΕΙ ΤΗΝ ΑΠΟΔΟΣΗ =====================

        /**
         * Βρίσκει ποιοι περιορισμοί δεσμεύουν στη βέλτιστη λύση και, για καθέναν, υπολογίζει ξανά τη βέλτιστη κατανομή
         * με χαλαρωμένο τον περιορισμό, ώστε να φανεί πόσο κέρδος «κλειδώνει».
         */
        static List<Insight> sensitivity(Assumptions a, PortfolioOptimizer.Solution solution) {
            EnumSet<AssetClass> all = Strategy.ALL.allowedClasses();
            double base = PortfolioOptimizer.optimize(a, all, SENSITIVITY_STEP)
                    .map(s -> s.metrics().netProfit()).orElse(solution.metrics().netProfit());

            Metrics m = solution.metrics();
            double[] w = solution.weights();
            List<Insight> out = new ArrayList<>();

            if (m.rwa() >= 0.98 * m.maxRwa()) {
                Assumptions r = a.copy();
                r.capitalRatio = a.capitalRatio * 0.90;
                add(out, "Κεφαλαιακή επάρκεια", "ο ελάχιστος δείκτης ιδίων κεφαλαίων προς σταθμισμένα στοιχεία (" + fmt(a.capitalRatio)
                        + "%) δεσμεύει", "μειωθεί κατά 10% (στο " + fmt(r.capitalRatio) + "%)", base, r, all);
            }
            if (m.varPctOfOwnFunds() >= 0.98 * a.varLimit) {
                Assumptions r = a.copy();
                r.varLimit = a.varLimit * 1.10;
                add(out, "Όριο κινδύνου", "η πιθανή ζημιά 99% φτάνει το όριο (" + fmt(a.varLimit) + "% των ιδίων κεφαλαίων)",
                        "αυξηθεί κατά 10% (στο " + fmt(r.varLimit) + "%)", base, r, all);
            }
            if (m.lcr() <= a.minLcr * 1.02) {
                Assumptions r = a.copy();
                r.outflowRate = a.outflowRate * 0.90;
                add(out, "Ρευστότητα (LCR)", "τα ρευστά διαθέσιμα είναι στο ελάχιστο που απαιτεί ο LCR",
                        "μειωθεί κατά 10% η υποθετική εκροή καταθέσεων (στο " + fmt(r.outflowRate) + "%)", base, r, all);
            }
            if (m.cash() <= m.minReserve() * 1.02 + 1) {
                Assumptions r = a.copy();
                r.reserveRatio = a.reserveRatio * 0.50;
                add(out, "Ελάχιστα αποθεματικά ΕΚΤ", "τα μετρητά είναι στο ελάχιστο των υποχρεωτικών αποθεματικών (0% απόδοση)",
                        "μειωθεί στο μισό ο δείκτης (στο " + fmt(r.reserveRatio) + "%)", base, r, all);
            }
            for (AssetClass c : AssetClass.values()) {
                int i = c.ordinal();
                if (w[i] > 0 && w[i] >= a.maxShare[i] / 100.0 - 0.0025) {
                    Assumptions r = a.copy();
                    r.maxShare[i] = Math.min(100, a.maxShare[i] + 5);
                    add(out, "Όριο συγκέντρωσης: " + c.label, "η κατηγορία έχει φτάσει το ανώτατο ποσοστό (" + fmt(a.maxShare[i]) + "%)",
                            "ανέβει κατά 5 ποσοστιαίες μονάδες (στο " + fmt(r.maxShare[i]) + "%)", base, r, all);
                }
            }
            out.sort(Comparator.comparingDouble(Insight::gain).reversed());
            return out;
        }

        private static void add(List<Insight> out, String name, String why, String relax, double base,
                                Assumptions relaxed, EnumSet<AssetClass> allowed) {
            double gain = PortfolioOptimizer.optimize(relaxed, allowed, SENSITIVITY_STEP)
                    .map(s -> s.metrics().netProfit() - base).orElse(0.0);
            String text;
            if (gain > base * 0.0005 + 1000) {
                text = String.format(GR, "%s: %s. Αν %s, το κέρδος θα αυξανόταν κατά %s (%+.1f%%).",
                        name, why, relax, euro(gain), base > 0 ? gain / base * 100 : 0.0);
            } else {
                text = String.format(GR, "%s: %s, αλλά η χαλάρωσή του δεν αυξάνει σημαντικά το κέρδος (αν %s).", name, why, relax);
                gain = 0;
            }
            out.add(new Insight(text, gain));
        }

        private static String fmt(double v) {
            return String.format(GR, "%.2f", v).replace(",00", "");
        }

        static String euro(double v) {
            return Math.abs(v) >= 1_000_000 ? String.format(GR, "%,.2f εκ. €", v / 1_000_000)
                    : String.format(GR, "%,.0f €", v);
        }
    }


