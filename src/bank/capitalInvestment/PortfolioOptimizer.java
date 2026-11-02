package bank.capitalInvestment;


    import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

    /**
     * Βρίσκει την κατανομή κεφαλαίου με το μεγαλύτερο καθαρό κέρδος που ικανοποιεί ΟΛΟΥΣ τους περιορισμούς
     * (ελάχιστα αποθεματικά, LCR, κεφαλαιακή επάρκεια, μόχλευση, όριο κινδύνου, όρια ανά κατηγορία).
     *
     * Μέθοδος: εξαντλητική αναζήτηση σε πλέγμα (π.χ. ανά 5% του κεφαλαίου) και μετά τοπική βελτίωση με μετακινήσεις
     * 5%, 2% και 1% μεταξύ δύο κατηγοριών. Με 8 κατηγορίες το πλέγμα έχει λίγες εκατοντάδες χιλιάδες συνδυασμούς.
     */
    public final class PortfolioOptimizer {
        private PortfolioOptimizer() {}

        public record Solution(double[] weights, Metrics metrics) {}

        public static Optional<Solution> optimize(Assumptions a, EnumSet<AssetClass> allowed, int stepPercent) {
            return optimize(a, allowed, stepPercent, true);
        }

        static Optional<Solution> optimize(Assumptions a, EnumSet<AssetClass> allowed, int stepPercent, boolean refine) {
            Evaluator ev = new Evaluator(a);
            int n = AssetClass.values().length;
            int units = 100 / stepPercent;

            int[] caps = new int[n];
            for (AssetClass c : AssetClass.values()) {
                caps[c.ordinal()] = allowed.contains(c) ? (int) Math.floor(a.maxShare[c.ordinal()] / stepPercent + 1e-9) : 0;
            }

            // Τα όρια ανά κατηγορία πρέπει να επιτρέπουν συνολικά 100%
            int capSum = 0;
            for (int c : caps) capSum += c;
            if (capSum < units) return Optional.empty();

            double[] best = new double[n];
            double[] bestValue = {Double.NEGATIVE_INFINITY};
            boolean[] found = {false};
            search(0, units, new int[n], caps, stepPercent, ev, best, bestValue, found);
            if (!found[0]) return Optional.empty();

            if (refine) improve(best, allowed, ev);
            return Optional.of(new Solution(best, ev.metrics(best)));
        }

        private static void search(int i, int left, int[] cur, int[] caps, int step, Evaluator ev,
                                   double[] best, double[] bestValue, boolean[] found) {
            int n = cur.length;
            if (i == n - 1) {
                if (left > caps[i]) return;
                cur[i] = left;
                double[] w = new double[n];
                for (int k = 0; k < n; k++) w[k] = cur[k] * step / 100.0;
                if (ev.feasible(w)) {
                    double v = ev.netProfit(w);
                    if (v > bestValue[0]) {
                        bestValue[0] = v;
                        System.arraycopy(w, 0, best, 0, n);
                        found[0] = true;
                    }
                }
                return;
            }
            int max = Math.min(left, caps[i]);
            for (int u = 0; u <= max; u++) {
                cur[i] = u;
                search(i + 1, left - u, cur, caps, step, ev, best, bestValue, found);
            }
            cur[i] = 0;
        }

        /** Τοπική βελτίωση: μετακινεί ποσοστό από μια κατηγορία σε άλλη όσο αυξάνεται το κέρδος και ισχύουν οι περιορισμοί. */
        private static void improve(double[] w, EnumSet<AssetClass> allowed, Evaluator ev) {
            int n = w.length;
            List<Integer> idx = new ArrayList<>();
            for (AssetClass c : allowed) idx.add(c.ordinal());

            for (double delta : new double[]{0.05, 0.02, 0.01}) {
                boolean improved = true;
                while (improved) {
                    improved = false;
                    double current = ev.netProfit(w);
                    int bi = -1, bj = -1;
                    double bestGain = 1e-6;
                    for (int i : idx) {
                        if (w[i] < delta - 1e-12) continue;
                        for (int j : idx) {
                            if (i == j || w[j] + delta > ev.cap(j) + 1e-12) continue;
                            w[i] -= delta; w[j] += delta;
                            if (ev.feasible(w)) {
                                double gain = ev.netProfit(w) - current;
                                if (gain > bestGain) { bestGain = gain; bi = i; bj = j; }
                            }
                            w[i] += delta; w[j] -= delta;
                        }
                    }
                    if (bi >= 0) {
                        w[bi] -= delta; w[bj] += delta;
                        improved = true;
                    }
                }
            }
            for (int k = 0; k < n; k++) if (Math.abs(w[k]) < 1e-12) w[k] = 0;
        }
    }


