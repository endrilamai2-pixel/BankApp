package LoanApp.LoanApp;

import java.util.Locale;
import java.util.Scanner;

public class Console {
    // ===================== ΕΠΙΚΟΙΝΩΝΙΑ ΜΕ ΤΟΝ ΧΡΗΣΤΗ =====================
    public static void run() {

        Scanner sc = new Scanner(System.in).useLocale(Locale.ENGLISH);
        System.out.println("=== Αξιολόγηση Αίτησης Δανείου ===\n");

        System.out.print("Ονοματεπώνυμο: ");
        String name = sc.nextLine().trim();

        // ---- Στοιχεία πιστοληπτικού ιστορικού -> αυτόματος υπολογισμός σκορ ----
        System.out.println("\n-- Στοιχεία πιστοληπτικού ιστορικού --");
        int late30 = readInt(sc, "Καθυστερήσεις πληρωμής 30+ ημερών (τελευταία 2 έτη): ", 0, 100);
        int late60 = readInt(sc, "Καθυστερήσεις πληρωμής 60+ ημερών: ", 0, 100);
        int late90 = readInt(sc, "Καθυστερήσεις πληρωμής 90+ ημερών: ", 0, 100);
        boolean bankrupt = readYesNo(sc, "Έχετε πτώχευση ή αθέτηση δανείου; (ν/ο): ");
        double limit = readDouble(sc, "Συνολικό όριο καρτών/πιστωτικών γραμμών (€): ", 0);
        double used = readDouble(sc, "Συνολικό χρησιμοποιημένο υπόλοιπο (€): ", 0);
        double histYears = readDouble(sc, "Έτη πιστωτικού ιστορικού: ", 0);
        int inquiries = readInt(sc, "Αιτήσεις πίστωσης τους τελευταίους 12 μήνες: ", 0, 100);
        int types = readInt(sc, "Είδη πίστωσης που έχετε (κάρτα, στεγαστικό, καταναλωτικό...): ", 0, 10);

        CreditScoreCalculator.ScoreResult sr = CreditScoreCalculator.calculate(
                new CreditScoreCalculator.CreditProfile(late30, late60, late90, bankrupt,
                        limit, used, histYears, inquiries, types));
        int score = sr.score();



        double assets = readDouble(sc, "Συνολική αξία περιουσιακών στοιχείων (€): ", 0);
        double amount = readDouble(sc, "Ποσό δανείου που ζητάτε (€): ", 1);
        int term = readInt(sc, "Διάρκεια σε έτη (1-" + LoanAppParameters.MAX_TERM_YEARS + "): ", 1, LoanAppParameters.MAX_TERM_YEARS);

        System.out.println("\nΣκοπός δανείου:");
        Purpose [] purposes = Purpose.values();
        for (int i = 0; i < purposes.length; i++) {
            System.out.println("  " + (i + 1) + ") " + purposes[i].label);
        }
        int choice = readInt(sc, "Επιλογή: ", 1, purposes.length);

        Main.Application app = new Main.Application(name, score, assets, amount, term, purposes[choice - 1]);
        Main.Decision d = Calculations.evaluate(app);

        System.out.println("\n----------- ΑΠΟΤΕΛΕΣΜΑ -----------");
        System.out.println("Αιτών:               " + app.name());
        System.out.println("Ιστορικό:            " + d.tier().label);
        System.out.println("Σκοπός:              " + app.purpose().label);
        if (d.approved()) {
            System.out.println("Απόφαση:             ΕΓΚΡΙΣΗ ✔");
            System.out.printf(Locale.US, "Επιτόκιο:            %.2f%%%n", d.rate());
            System.out.printf(Locale.US, "Μηνιαία δόση:        %.2f €%n", d.monthlyPayment());
            System.out.printf(Locale.US, "Συνολικό κόστος:     %.2f €%n", d.totalCost());
            System.out.printf(Locale.US, "Συνολικοί τόκοι:     %.2f €%n", d.totalCost() - app.amount());
        } else {
            System.out.println("Απόφαση:             ΑΠΟΡΡΙΨΗ ✘");
            System.out.println("Λόγος:               " + d.reason());
        }
    }


        public static boolean readYesNo(Scanner sc, String prompt) {
            while (true) {
                System.out.print(prompt);
                String v = sc.nextLine().trim().toLowerCase();
                if (v.equals("ν") || v.equals("ναι") || v.equals("y") || v.equals("yes")) return true;
                if (v.equals("ο") || v.equals("όχι") || v.equals("οχι") || v.equals("n") || v.equals("no")) return false;
                System.out.println("  Απαντήστε ν ή ο.");
            }
        }



    public static int readInt(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("  Μη έγκυρη τιμή (" + min + "-" + max + "). Ξαναδοκιμάστε.");
        }
    }

    public static double readDouble(Scanner sc, String prompt, double min) {
        while (true) {
            System.out.print(prompt);
            try {
                double v = Double.parseDouble(sc.nextLine().trim().replace(',', '.'));
                if (v >= min) return v;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("  Μη έγκυρη τιμή. Ξαναδοκιμάστε.");
        }
    }
}
