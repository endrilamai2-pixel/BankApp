package LoanApp.LoanApp;

    import java.util.Optional;

    /** Υπηρεσία αναζήτησης επιχείρησης σε μητρώο. Επιτρέπει χρήση της υπηρεσίας ΑΑΔΕ. */
    public interface CompanyRegistryService {

        /** Αναζήτηση με ΑΦΜ. Επιστρέφει empty αν δεν βρεθεί· ρίχνει RegistryException σε σφάλμα επικοινωνίας. */
        Optional<CompanyRecord> findByTaxId(String taxId);

        /** Περιγραφή της πηγής, για εμφάνιση στον χρήστη (ώστε να φαίνεται αν τα δεδομένα είναι πραγματικά). */
        String sourceName();
    }


