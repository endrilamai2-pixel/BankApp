package bank.kyc;

/** Σφάλμα επικοινωνίας ή χρήσης της υπηρεσίας της ΑΑΔΕ (δίκτυο, λάθος κωδικοί, όριο κλήσεων κ.λπ.). */
public class AadeException extends RuntimeException {
    public AadeException(String message) { super(message); }
    public AadeException(String message, Throwable cause) { super(message, cause); }
}
