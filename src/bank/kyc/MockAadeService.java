package bank.kyc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Δοκιμαστική υπηρεσία με ΦΑΝΤΑΣΤΙΚΑ στοιχεία, για επίδειξη χωρίς διαπιστευτήρια ΑΑΔΕ.
 * ΔΕΝ περιέχει πραγματικά δεδομένα και ΔΕΝ μετράει ως έλεγχος KYC (isLive() = false).
 */
public class MockAadeService implements AadeService {

    private static AadeRecord person(String afm, String name, String flag, String flagDescr) {
        return new AadeRecord(afm, name, "", "", true, flag, flagDescr, "", "Δ ΑΘΗΝΩΝ", "ΟΔΟΣ ΔΟΚΙΜΩΝ 1, ΑΘΗΝΑ",
                LocalDate.of(2005, 5, 1), null, List.of());
    }

    private static AadeRecord firm(String afm, String name, String legal, String flag, String flagDescr,
                                   LocalDate stop, String city, String kad, String kadDescr) {
        return new AadeRecord(afm, name, "", legal, false, flag, flagDescr, "ΕΠΙΤΗΔΕΥΜΑΤΙΑΣ", "Δ ΑΘΗΝΩΝ",
                "ΟΔΟΣ ΔΟΚΙΜΩΝ 2, " + city, LocalDate.of(2012, 3, 15), stop,
                List.of(new AadeRecord.Activity(kad, kadDescr, true)));
    }

    private static final Map<String, AadeRecord> DATA = Map.of(
            "100000015", person("100000015", "ΔΟΚΙΜΑΣΤΙΚΟΣ ΓΙΩΡΓΟΣ", "1", "ΕΝΕΡΓΟΣ ΑΦΜ"),
            "100000027", person("100000027", "ΠΑΡΑΔΕΙΓΜΑΤΙΔΟΥ ΜΑΡΙΑ", "1", "ΕΝΕΡΓΟΣ ΑΦΜ"),
            "100000039", person("100000039", "ΑΝΕΝΕΡΓΟΥ ΝΙΚΟΣ", "2", "ΑΝΕΝΕΡΓΟΣ ΑΦΜ"),
            "123456783", firm("123456783", "ΑΛΦΑ ΤΡΟΦΙΜΑ Α.Ε.", "ΑΝΩΝΥΜΗ ΕΤΑΙΡΕΙΑ", "1", "ΕΝΕΡΓΟΣ ΑΦΜ",
                    null, "ΑΘΗΝΑ", "10.89.19", "Παραγωγή άλλων ειδών διατροφής"),
            "234567897", firm("234567897", "ΒΗΤΑ ΥΠΗΡΕΣΙΕΣ Ο.Ε.", "ΟΜΟΡΡΥΘΜΗ ΕΤΑΙΡΕΙΑ", "1", "ΕΝΕΡΓΟΣ ΑΦΜ",
                    null, "ΘΕΣΣΑΛΟΝΙΚΗ", "62.01", "Δραστηριότητες προγραμματισμού ηλεκτρονικών υπολογιστών"),
            "345678914", firm("345678914", "ΓΑΜΜΑ ΜΕΤΑΦΟΡΕΣ Ι.Κ.Ε.", "ΙΔΙΩΤΙΚΗ ΚΕΦΑΛΑΙΟΥΧΙΚΗ ΕΤΑΙΡΕΙΑ", "1", "ΕΝΕΡΓΟΣ ΑΦΜ",
                    LocalDate.of(2024, 12, 31), "ΠΑΤΡΑ", "49.41", "Εμπορευματικές οδικές μεταφορές"),
            "456789125", firm("456789125", "ΔΕΛΤΑ ΚΑΤΑΣΚΕΥΕΣ Α.Ε.", "ΑΝΩΝΥΜΗ ΕΤΑΙΡΕΙΑ", "2", "ΑΝΕΝΕΡΓΟΣ ΑΦΜ",
                    LocalDate.of(2023, 6, 30), "ΗΡΑΚΛΕΙΟ", "41.20", "Εκτέλεση κατασκευαστικών έργων")
    );

    @Override
    public Optional<AadeRecord> lookup(String afm) {
        return Optional.ofNullable(DATA.get(afm));
    }

    @Override
    public String sourceName() {
        return "δοκιμαστικά δεδομένα (δεν έχουν οριστεί διαπιστευτήρια ΑΑΔΕ)";
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
