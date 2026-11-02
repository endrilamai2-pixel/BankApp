package bank.capitalInvestment;


    import java.util.EnumSet;
import java.util.Set;

    /** Οι «κινήσεις» που συγκρίνονται: ποιες κατηγορίες τοποθέτησης επιτρέπονται σε κάθε περίπτωση. */
    public enum Strategy {
        MARKETS("Τοποθέτηση σε μετοχές και ομόλογα",
                EnumSet.of(AssetClass.Group.LIQUIDITY, AssetClass.Group.MARKETS)),
        LENDING("Δανεισμός πελατών",
                EnumSet.of(AssetClass.Group.LIQUIDITY, AssetClass.Group.LENDING)),
        ALL("Βέλτιστο μίγμα (όλες οι επιλογές)",
                EnumSet.allOf(AssetClass.Group.class));

        public final String label;
        private final Set<AssetClass.Group> groups;

        Strategy(String label, Set<AssetClass.Group> groups) {
            this.label = label;
            this.groups = groups;
        }

        /** Οι κατηγορίες που επιτρέπονται. Η ρευστότητα (μετρητά, κρατικά) είναι πάντα διαθέσιμη. */
        public EnumSet<AssetClass> allowedClasses() {
            EnumSet<AssetClass> set = EnumSet.noneOf(AssetClass.class);
            for (AssetClass c : AssetClass.values()) {
                if (groups.contains(c.group)) set.add(c);
            }
            return set;
        }
    }


