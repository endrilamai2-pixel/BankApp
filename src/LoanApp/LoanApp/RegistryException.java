package LoanApp.LoanApp;


    /** Σφάλμα κατά την επικοινωνία με το μητρώο (δίκτυο, άκυρο key, όριο αιτημάτων κ.λπ.). */
    public class RegistryException extends RuntimeException {
        public RegistryException(String message) { super(message); }
        public RegistryException(String message, Throwable cause) { super(message, cause); }
    }


