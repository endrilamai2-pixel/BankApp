package bank.kyc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Συγκρίνει τα στοιχεία που δήλωσε ο υπάλληλος με αυτά της ΑΑΔΕ και βγάζει απόφαση KYC. */
public final class KycChecker {

    public enum Status {
        /** Όλα ταιριάζουν και ο έλεγχος έγινε στην πραγματική υπηρεσία. */
        VERIFIED("Επαληθεύτηκε"),
        /** Ασυμφωνία που θέλει ανθρώπινη κρίση (π.χ. διαφορετική επωνυμία). */
        WARNING("Προειδοποίηση"),
        /** Το ΑΦΜ δεν υπάρχει, είναι ανενεργό ή δεν ταιριάζει ο τύπος πελάτη: δεν επιτρέπεται η εγγραφή. */
        REJECTED("Απορρίφθηκε"),
        /** Δεν έγινε έλεγχος (σφάλμα επικοινωνίας ή δοκιμαστική υπηρεσία). */
        UNAVAILABLE("Δεν ολοκληρώθηκε");

        public final String label;
        Status(String label) { this.label = label; }
    }

    public record Outcome(Status status, AadeRecord record, List<String> messages, String source) { }

    private KycChecker() { }

    public static Outcome checkIndividual(AadeService svc, String afm, String firstName, String lastName) {
        return run(svc, afm, false, firstName + " " + lastName);
    }

    public static Outcome checkBusiness(AadeService svc, String afm, String companyName) {
        return run(svc, afm, true, companyName);
    }

    private static Outcome run(AadeService svc, String afm, boolean business, String declaredName) {
        AadeRecord rec;
        try {
            Optional<AadeRecord> found = svc.lookup(afm);
            if (found.isEmpty()) {
                return new Outcome(Status.REJECTED, null,
                        List.of("Το ΑΦΜ " + afm + " δεν βρέθηκε στο μητρώο της ΑΑΔΕ."), svc.sourceName());
            }
            rec = found.get();
        } catch (AadeException e) {
            return new Outcome(Status.UNAVAILABLE, null, List.of(e.getMessage()), svc.sourceName());
        }

        List<String> rejects = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!rec.isActiveAfm()) {
            rejects.add("Το ΑΦΜ δεν είναι ενεργό στην ΑΑΔΕ" +
                    (rec.deactivationDescr().isEmpty() ? "." : " (" + rec.deactivationDescr() + ")."));
        }
        if (business && rec.hasStopped()) {
            rejects.add("Έχει δηλωθεί παύση εργασιών της επιχείρησης στις " + rec.stopDate() + ".");
        }
        if (!business && !rec.naturalPerson()) {
            rejects.add("Το ΑΦΜ ανήκει σε νομικό πρόσωπο ή οντότητα, όχι σε φυσικό πρόσωπο.");
        }

        boolean nameOk = business
                ? businessNameMatches(declaredName, rec.name(), rec.tradeTitle())
                : individualNameMatches(declaredName, rec.name());
        if (!nameOk) {
            warnings.add("Το όνομα/η επωνυμία που δηλώθηκε (\"" + declaredName.trim() + "\") δεν ταιριάζει με της ΑΑΔΕ (\""
                    + rec.name() + "\").");
        }

        List<String> messages = new ArrayList<>(rejects);
        messages.addAll(warnings);

        Status status;
        if (!rejects.isEmpty()) {
            status = Status.REJECTED;
        } else if (!warnings.isEmpty()) {
            status = Status.WARNING;
        } else if (!svc.isLive()) {
            status = Status.UNAVAILABLE;
            messages.add("Ο έλεγχος έγινε με δοκιμαστικά δεδομένα και δεν θεωρείται έλεγχος KYC.");
        } else {
            status = Status.VERIFIED;
            messages.add("Το ΑΦΜ είναι ενεργό και τα στοιχεία ταιριάζουν με την ΑΑΔΕ.");
        }
        return new Outcome(status, rec, messages, svc.sourceName());
    }

    // ---------------------------------------------------------------- σύγκριση ονομάτων

    /** Όλα τα λεκτικά του ονόματος και του επωνύμου πρέπει να υπάρχουν στην επωνυμία της ΑΑΔΕ (με οποιαδήποτε σειρά). */
    static boolean individualNameMatches(String declared, String aadeName) {
        Set<String> want = tokens(declared);
        Set<String> have = tokens(aadeName);
        return !want.isEmpty() && have.containsAll(want);
    }

    /** Η επωνυμία ταιριάζει αν, αγνοώντας τις νομικές μορφές (Α.Ε., Ι.Κ.Ε. κ.λπ.), το ένα σύνολο λέξεων περιέχεται στο άλλο. */
    static boolean businessNameMatches(String declared, String aadeName, String tradeTitle) {
        Set<String> want = significant(tokens(declared));
        if (want.isEmpty()) return false;
        for (String candidate : new String[]{aadeName, tradeTitle}) {
            Set<String> have = significant(tokens(candidate));
            if (have.isEmpty()) continue;
            if (have.containsAll(want) || want.containsAll(have)) return true;
        }
        return false;
    }

    private static final Set<String> LEGAL_FORM_WORDS = Set.of(
            "Α", "Ε", "Ο", "Ι", "Κ", "Π", "Β", "ΑΕ", "ΕΠΕ", "ΙΚΕ", "ΟΕ", "ΕΕ", "ΜΟΝ", "ΜΟΝΟΠΡΟΣΩΠΗ", "ΑΝΩΝΥΜΗ", "ΕΤΑΙΡΕΙΑ",
            "ΕΤΑΙΡΙΑ", "ΙΔΙΩΤΙΚΗ", "ΚΕΦΑΛΑΙΟΥΧΙΚΗ", "ΟΜΟΡΡΥΘΜΗ", "ΕΤΕΡΟΡΡΥΘΜΗ", "ΠΕΡΙΟΡΙΣΜΕΝΗΣ", "ΕΥΘΥΝΗΣ", "ΚΑΙ", "ΣΙΑ",
            "ΑΒΕΕ", "ΒΕΕ", "SA", "LTD", "IKE", "AE", "OE", "EE", "EPE");

    private static Set<String> significant(Set<String> tokens) {
        Set<String> out = new HashSet<>(tokens);
        out.removeAll(LEGAL_FORM_WORDS);
        return out;
    }

    /** Κεφαλαία, χωρίς τόνους, σε λέξεις (τελικό «ς» → «Σ» ώστε το «ΓΙΩΡΓΟΣ» να ταιριάζει με το «Γιώργος»). */
    static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('ς', 'σ').toUpperCase();
        for (String t : n.split("[^\\p{L}\\p{N}]+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
