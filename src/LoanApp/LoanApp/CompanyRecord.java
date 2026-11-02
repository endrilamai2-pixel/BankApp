package LoanApp.LoanApp;


    import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

    /** Τα βασικά στοιχεία επιχείρησης που επιστρέφονται από την ΑΑΔΕ. */
    public record CompanyRecord(String taxId, String legalName, String legalForm,
                                String status, LocalDate incorporationDate, String address) {

        /** Ενεργή = η κατάσταση περιέχει «ενεργή» (όχι «ανενεργή») και δεν υπάρχει πτώχευση/εξυγίανση. */
        public boolean isActive() {
            String n = norm(status);
            return n.contains("ενεργ") && !n.contains("ανενεργ") && !hasInsolvencyProceedings();
        }

        /** Πτώχευση, εξυγίανση ή συνδιαλλαγή. */
        public boolean hasInsolvencyProceedings() {
            String n = norm(status);
            return n.contains("πτωχευ") || n.contains("εξυγιανσ") || n.contains("συνδιαλλαγ");
        }

        /** Έτη από τη σύσταση (1 δεκαδικό), ή 0 αν δεν είναι γνωστή η ημερομηνία. */
        public double yearsInOperation(LocalDate today) {
            if (incorporationDate == null) return 0;
            double years = ChronoUnit.DAYS.between(incorporationDate, today) / 365.25;
            return Math.max(0, Math.round(years * 10) / 10.0);
        }

        /** Πεζά και χωρίς τόνους, ώστε το «Ενεργή» να ταιριάζει με το «ενεργη». */
        static String norm(String s) {
            if (s == null) return "";
            return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        }
    }


