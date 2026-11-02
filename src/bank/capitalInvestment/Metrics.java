package bank.capitalInvestment;


    /** Τα αποτελέσματα ενός χαρτοφυλακίου: κέρδος, κίνδυνος και ρυθμιστικοί δείκτες. */
    public record Metrics(
            double[] amounts,            // ποσά ανά κατηγορία (€)
            double profitBeforeTax,      // κέρδος προ φόρων (€)
            double netProfit,            // κέρδος μετά από φόρους (€)
            double roe,                  // απόδοση ιδίων κεφαλαίων, % (μετά από φόρους)
            double rwa,                  // σταθμισμένα στοιχεία ενεργητικού (€)
            double maxRwa,               // μέγιστα επιτρεπτά σταθμισμένα στοιχεία (€)
            double capitalRatio,         // ίδια κεφάλαια / σταθμισμένα στοιχεία, % (Infinity αν RWA = 0)
            double leverageRatio,        // ίδια κεφάλαια / ενεργητικό, %
            double lcr,                  // %
            double cash,                 // μετρητά (€)
            double minReserve,           // ελάχιστα αποθεματικά ΕΚΤ (€)
            double sigma,                // ετήσια τυπική απόκλιση χαρτοφυλακίου, % του ενεργητικού
            double var99,                // ζημιά 99% ενός έτους (€)
            double varPctOfOwnFunds) {   // VaR ως % των ιδίων κεφαλαίων
    }


