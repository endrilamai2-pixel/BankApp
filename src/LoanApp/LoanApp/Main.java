package LoanApp.LoanApp;


/**
     * Εφαρμογή αξιολόγησης δανείων.
     * Τρέξιμο:  java LoanApp.java
     *
     * Κριτήρια: πιστοληπτικό ιστορικό (credit score 300-850), περιουσιακά στοιχεία (assets),
     * σκοπός δανείου (επένδυση / αναψυχή / προσωπική χρήση).
     */
    public class Main {


        // ===================== ΑΙΤΗΣΗ & ΑΠΟΤΕΛΕΣΜΑ =====================
       public record Application(String name, int creditScore, double assets,
                           double amount, int termYears, Purpose purpose) {}

        public record Decision(boolean approved, String reason, CreditTier tier,
                        double rate, double monthlyPayment, double totalCost) {}

        public static Decision reject(CreditTier tier, String reason) {
            return new Decision(false, reason, tier, 0, 0, 0);
        }

    public static void main(String[] args) {
           bank.app.AppLauncher.start();
        //Console.run();
    }

    }

