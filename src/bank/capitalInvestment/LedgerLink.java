package bank.capitalInvestment;

import bank.ledger.AccountType;
import bank.ledger.ChartOfAccounts;
import bank.ledger.LedgerDAO;
import bank.ledger.LedgerDAO.AccountBalance;
import bank.ledger.LedgerService;
import bank.registration.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Γέφυρα ανάμεσα στο γενικό καθολικό (πραγματικά υπόλοιπα της τράπεζας) και στο μοντέλο βελτιστοποίησης:
 * διαβάζει τη θέση της τράπεζας, ελέγχει μια επένδυση απέναντι στους ρυθμιστικούς περιορισμούς και την καταχωρεί.
 *
 * Αντιστοίχιση λογαριασμών καθολικού σε κατηγορίες του μοντέλου (απλοποίηση του project):
 * Ταμείο και διαθέσιμα στην ΤτΕ → Μετρητά · χορηγήσεις σε ιδιώτες → Καταναλωτικά · χορηγήσεις σε επιχειρήσεις → ΜμΕ.
 */
final class LedgerLink {
    private LedgerLink() { }

    private static final Locale GR = Locale.forLanguageTag("el-GR");

    /** Οι κατηγορίες στις οποίες μπορεί να επενδύσει η τράπεζα από το ταμείο, και ο λογαριασμός που τις κρατά. */
    static final Map<AssetClass, String> INVESTABLE = new EnumMap<>(AssetClass.class);
    static {
        INVESTABLE.put(AssetClass.GOV_BONDS, ChartOfAccounts.GOV_BONDS);
        INVESTABLE.put(AssetClass.CORP_BONDS, ChartOfAccounts.CORP_BONDS);
        INVESTABLE.put(AssetClass.EQUITIES, ChartOfAccounts.EQUITIES);
    }

    /** Η θέση της τράπεζας στο καθολικό. Όλα τα ποσά σε ευρώ. */
    record Position(double[] amounts, double totalAssets, double ownFunds, double deposits, double spendableCash) { }

    static Position load() throws SQLException {
        return positionFrom(LedgerDAO.balances());
    }

    static Position positionFrom(List<AccountBalance> balances) {
        double[] amounts = new double[AssetClass.values().length];
        double totalAssets = 0, deposits = 0, equity = 0, result = 0, spendable = 0;
        for (AccountBalance b : balances) {
            if (!b.account().isLeaf()) continue;
            double net = b.net().doubleValue();
            String code = b.account().code();
            AccountType type = b.account().type();
            if (type == AccountType.ASSET) totalAssets += net;
            switch (code) {
                case ChartOfAccounts.CASH -> { amounts[AssetClass.CASH.ordinal()] += net; spendable += net; }
                case ChartOfAccounts.CENTRAL_BANK -> amounts[AssetClass.CASH.ordinal()] += net;
                case ChartOfAccounts.GOV_BONDS -> amounts[AssetClass.GOV_BONDS.ordinal()] += net;
                case ChartOfAccounts.CORP_BONDS -> amounts[AssetClass.CORP_BONDS.ordinal()] += net;
                case ChartOfAccounts.EQUITIES -> amounts[AssetClass.EQUITIES.ordinal()] += net;
                case ChartOfAccounts.LOANS_INDIVIDUALS -> amounts[AssetClass.CONSUMER.ordinal()] += net;
                case ChartOfAccounts.LOANS_BUSINESSES -> amounts[AssetClass.SME.ordinal()] += net;
                case ChartOfAccounts.DEPOSITS_INDIVIDUALS, ChartOfAccounts.DEPOSITS_BUSINESSES -> deposits += net;
                default -> { }
            }
            if (type == AccountType.EQUITY) equity += net;
            if (type == AccountType.INCOME) result += net;
            if (type == AccountType.EXPENSE) result -= net;
        }
        return new Position(amounts, totalAssets, equity + result, deposits, spendable);
    }

    /**
     * Ποιοι περιορισμοί του μοντέλου παραβιάζονται αν η θέση γίνει {@code newAmounts}. Κενή λίστα = όλα εντάξει.
     * Το ενεργητικό και τα ίδια κεφάλαια λαμβάνονται από το καθολικό, οι υπόλοιπες παραδοχές από την οθόνη.
     */
    static List<String> violations(Assumptions base, Position pos, double[] newAmounts) {
        List<String> out = new ArrayList<>();
        if (pos.totalAssets() <= 0 || pos.ownFunds() <= 0 || pos.deposits() <= 0) {
            out.add("Το καθολικό δεν έχει ενεργητικό, καταθέσεις ή ίδια κεφάλαια για να γίνει έλεγχος περιορισμών.");
            return out;
        }
        Assumptions a = base.copy();
        a.funds = pos.totalAssets();
        a.ownFunds = pos.ownFunds();
        Evaluator ev = new Evaluator(a);
        double[] w = new double[newAmounts.length];
        for (int i = 0; i < w.length; i++) w[i] = newAmounts[i] / a.funds;
        Metrics m = ev.metrics(w);

        if (m.cash() < m.minReserve() - 1) {
            out.add(String.format(GR, "Τα μετρητά (%s) θα πέσουν κάτω από τα ελάχιστα αποθεματικά ΕΚΤ (%s).",
                    Analysis.euro(m.cash()), Analysis.euro(m.minReserve())));
        }
        if (m.lcr() < a.minLcr - 1e-6) {
            out.add(String.format(GR, "Ο δείκτης LCR θα είναι %.0f%%, κάτω από το ελάχιστο %.0f%%.", m.lcr(), a.minLcr));
        }
        if (m.rwa() > m.maxRwa() + 1) {
            out.add(String.format(GR, "Τα σταθμισμένα στοιχεία ενεργητικού (%s) θα ξεπεράσουν το μέγιστο που επιτρέπουν τα ίδια κεφάλαια (%s).",
                    Analysis.euro(m.rwa()), Analysis.euro(m.maxRwa())));
        }
        if (m.varPctOfOwnFunds() > a.varLimit + 1e-6) {
            out.add(String.format(GR, "Η ζημιά 99%% θα είναι %.0f%% των ιδίων κεφαλαίων, πάνω από το όριο %.0f%%.",
                    m.varPctOfOwnFunds(), a.varLimit));
        }
        for (AssetClass c : INVESTABLE.keySet()) {
            int i = c.ordinal();
            if (w[i] > ev.cap(i) + 1e-9) {
                out.add(String.format(GR, "Η κατηγορία «%s» θα φτάσει το %.1f%% του ενεργητικού, πάνω από το μέγιστο %.0f%%.",
                        c.label, w[i] * 100, ev.cap(i) * 100));
            }
        }
        return out;
    }

    /** Νέα θέση μετά από αγορές (τα μετρητά μειώνονται κατά το σύνολο). */
    static double[] after(Position pos, Map<AssetClass, Double> purchases) {
        double[] n = pos.amounts().clone();
        for (Map.Entry<AssetClass, Double> e : purchases.entrySet()) {
            n[e.getKey().ordinal()] += e.getValue();
            n[AssetClass.CASH.ordinal()] -= e.getValue();
        }
        return n;
    }

    /**
     * Προτεινόμενες αγορές για να πλησιάσει η πραγματική θέση τη βέλτιστη κατανομή του μοντέλου: για κάθε κατηγορία
     * που μπορεί να αγοραστεί, η διαφορά ανάμεσα στο στόχο και στη σημερινή θέση, χωρίς να ξοδευτούν περισσότερα
     * μετρητά απ' όσα υπερβαίνουν τον στόχο σε μετρητά. Τα δάνεια δεν είναι αγορά από το ταμείο και δεν περιλαμβάνονται.
     */
    static Map<AssetClass, Double> suggest(Position pos, double[] targetWeights) {
        Map<AssetClass, Double> out = new EnumMap<>(AssetClass.class);
        double total = 0;
        for (AssetClass c : INVESTABLE.keySet()) {
            int i = c.ordinal();
            double need = Math.max(0, targetWeights[i] * pos.totalAssets() - pos.amounts()[i]);
            out.put(c, need);
            total += need;
        }
        double targetCash = targetWeights[AssetClass.CASH.ordinal()] * pos.totalAssets();
        double budget = Math.max(0, Math.min(pos.spendableCash(), pos.amounts()[AssetClass.CASH.ordinal()] - targetCash));
        double scale = total > budget && total > 0 ? budget / total : 1.0;
        out.replaceAll((c, v) -> Math.floor(v * scale));
        return out;
    }

    /** Καταχωρεί τις αγορές στο καθολικό, σε ΜΙΑ συναλλαγή: ή περνούν όλες ή καμία. Ελέγχει ότι υπάρχουν μετρητά στο ταμείο. */
    static void invest(Map<AssetClass, BigDecimal> purchases, String user) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : purchases.values()) total = total.add(v);
        if (total.signum() <= 0) throw new IllegalArgumentException("Το ποσό της επένδυσης πρέπει να είναι θετικό.");
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                BigDecimal cash = LedgerDAO.netDebit(c, ChartOfAccounts.CASH);
                if (cash.compareTo(total) < 0) {
                    throw new SQLException(String.format(GR, "Ανεπαρκή μετρητά στο ταμείο: διαθέσιμα %s, ζητούνται %s.",
                            Analysis.euro(cash.doubleValue()), Analysis.euro(total.doubleValue())));
                }
                for (Map.Entry<AssetClass, BigDecimal> e : purchases.entrySet()) {
                    if (e.getValue().signum() <= 0) continue;
                    LedgerService.postInvestment(c, INVESTABLE.get(e.getKey()), e.getValue(), e.getKey().label, user);
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
