package bank.capitalInvestment;



    /**
     * Οι επιλογές τοποθέτησης της τράπεζας, με ενδεικτικές προεπιλεγμένες παραμέτρους (ΟΛΕΣ επεξεργάσιμες στην εφαρμογή).
     *
     * Οι αποδόσεις, οι ζημίες και οι μεταβλητότητες είναι ΥΠΟΘΕΣΕΙΣ για εκπαιδευτική χρήση και όχι προβλέψεις αγοράς.
     * Οι συντελεστές στάθμισης κινδύνου ακολουθούν την τυποποιημένη προσέγγιση του CRR/CRR3 (απλοποιημένα):
     *   - Μετρητά κεντρικής τράπεζας, κρατικά ομόλογα σε ευρώ: 0%
     *   - Μετοχές: 130% για το 2026 (μεταβατικό καθεστώς του άρθρου 495α CRR3: 100% το 2025, 130% το 2026,
     *     160% το 2027, 190% το 2028, 220% το 2029 και 250% από το 2030)
     *   - Στεγαστικά: περίπου 35% (ενδεικτικό: εξαρτάται από τον λόγο δανείου προς αξία ακινήτου)
     *   - Λιανική (καταναλωτικά): 75% | ΜμΕ: 85% | Εταιρικά δάνεια χωρίς αξιολόγηση: 100% | Εταιρικά ομόλογα: 75% (ενδεικτικό)
     */
    public enum AssetClass {
        //                                   ομάδα           HQLA   απόδοση ζημιά  λειτ.   μεταβλ. RW   όριο
        CASH      ("Μετρητά στην κεντρική τράπεζα", Group.LIQUIDITY, true,  2.25,  0.00,  0.00,  0.0,    0, 100),
        GOV_BONDS ("Κρατικά ομόλογα (ΕΕ/Ελλάδα)",   Group.LIQUIDITY, true,  3.20,  0.05,  0.05,  6.0,    0,  60),
        CORP_BONDS("Εταιρικά ομόλογα (επενδυτικής βαθμίδας)", Group.MARKETS, false, 4.00, 0.15, 0.10, 7.0, 75, 25),
        EQUITIES  ("Μετοχές (εισηγμένες)",          Group.MARKETS,   false, 7.00,  0.00,  0.20, 20.0,  130,  10),
        MORTGAGES ("Στεγαστικά δάνεια",             Group.LENDING,   false, 4.30,  0.25,  0.70,  3.0,   35,  25),
        CONSUMER  ("Καταναλωτικά δάνεια και κάρτες", Group.LENDING,  false, 9.00,  3.00,  3.00,  4.5,   75,  10),
        SME       ("Δάνεια σε μικρομεσαίες (ΜμΕ)",  Group.LENDING,   false, 6.00,  1.20,  1.50,  5.0,   85,  25),
        CORPORATE ("Δάνεια σε μεγάλες επιχειρήσεις", Group.LENDING,  false, 5.00,  0.50,  0.40,  4.0,  100,  30);

        /** Για τις στρατηγικές: η ρευστότητα (μετρητά, κρατικά ομόλογα) χρειάζεται σε κάθε περίπτωση. */
        public enum Group { LIQUIDITY, MARKETS, LENDING }

        public final String label;
        public final Group group;
        /** Αν μετράει ως υψηλής ποιότητας ρευστά διαθέσιμα (HQLA επιπέδου 1) για τον δείκτη LCR. */
        public final boolean hqla;
        public final double defaultReturn, defaultLoss, defaultOpCost, defaultVolatility, defaultRiskWeight, defaultMaxShare;

        AssetClass(String label, Group group, boolean hqla, double ret, double loss, double opCost,
                   double vol, double riskWeight, double maxShare) {
            this.label = label;
            this.group = group;
            this.hqla = hqla;
            this.defaultReturn = ret;
            this.defaultLoss = loss;
            this.defaultOpCost = opCost;
            this.defaultVolatility = vol;
            this.defaultRiskWeight = riskWeight;
            this.defaultMaxShare = maxShare;
        }
    }


