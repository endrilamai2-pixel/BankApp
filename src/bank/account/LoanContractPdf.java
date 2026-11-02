package bank.account;

import bank.pdf.FontLocator;
import bank.pdf.PdfBuilder;
import bank.pdf.PdfBuilder.Align;
import bank.pdf.TtfFont;
import bank.registration.IbanGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Δημιουργία της σύμβασης δανείου σε PDF (με πίνακα αποπληρωμής ως παράρτημα). */
public final class LoanContractPdf {

    private static final Locale GR = Locale.forLanguageTag("el-GR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LoanContractPdf() { }

    public static void write(ContractData d, Path out) throws IOException {
        TtfFont regular = FontLocator.regular();
        TtfFont bold = FontLocator.bold();
        if (regular == null) {
            throw new IOException("Δεν βρέθηκε γραμματοσειρά με ελληνικά στον υπολογιστή. Βάλε ένα αρχείο .ttf "
                    + "(π.χ. Arial) στον φάκελο «fonts» με όνομα font.ttf, ή όρισε -Dbank.pdf.font=διαδρομή.ttf");
        }
        byte[] pdf = build(d, regular, bold);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.write(out, pdf);
    }

    static byte[] build(ContractData d, TtfFont regular, TtfFont bold) throws IOException {
        int months = d.termYears() * 12;
        List<AmortizationSchedule.Row> schedule =
                AmortizationSchedule.build(d.amount(), d.ratePercent(), months, d.monthlyPayment());
        BigDecimal totalRepayment = AmortizationSchedule.total(schedule);
        BigDecimal totalInterest = totalRepayment.subtract(d.amount());
        String date = d.date().format(DATE);

        PdfBuilder p = new PdfBuilder(regular, bold, "Σύμβαση Δανείου #" + d.loanId(),
                BankInfo.NAME + " · Σύμβαση δανείου #" + d.loanId());

        p.paragraph(BankInfo.NAME.toUpperCase(GR), true, 11, Align.CENTER, 0, true);
        p.title("ΣΥΜΒΑΣΗ ΔΑΝΕΙΟΥ");
        p.paragraph("Αριθμός σύμβασης: " + d.loanId() + "   ·   Ημερομηνία: " + date, false, 10, Align.CENTER, 0, true);
        p.space(8);

        p.paragraph("Στις " + date + " συνάπτεται η παρούσα σύμβαση μεταξύ των παρακάτω συμβαλλομένων:",
                false, 10.5f, Align.LEFT, 0, false);

        // ---- Συμβαλλόμενοι
        p.heading("Α. Συμβαλλόμενοι");
        List<String[]> parties = new ArrayList<>();
        parties.add(new String[]{"Δανειοδότρια", BankInfo.NAME + ", " + BankInfo.ADDRESS});
        parties.add(new String[]{d.isIndividual() ? "Δανειολήπτης" : "Δανειολήπτρια επιχείρηση", d.name()});
        parties.add(new String[]{"ΑΦΜ", d.afm()});
        if (d.isIndividual()) {
            parties.add(new String[]{"Αριθμός ταυτότητας", nz(d.idNumber())});
            parties.add(new String[]{"Επάγγελμα", nz(d.profession())});
            parties.add(new String[]{"Διεύθυνση κατοικίας", nz(d.address())});
        } else {
            parties.add(new String[]{"Έδρα", nz(d.address())});
            parties.add(new String[]{"Δραστηριότητα", nz(d.profession())});
        }
        p.keyValueTable(parties);

        // ---- Όροι δανείου
        p.heading("Β. Βασικοί όροι δανείου");
        List<String[]> terms = new ArrayList<>();
        terms.add(new String[]{"Ποσό δανείου", money(d.amount())});
        terms.add(new String[]{"Σκοπός δανείου", d.purpose()});
        terms.add(new String[]{"Διάρκεια", d.termYears() + (d.termYears() == 1 ? " έτος" : " έτη") + " (" + months + " μηνιαίες δόσεις)"});
        terms.add(new String[]{"Ετήσιο επιτόκιο", String.format(GR, "%.2f%%", d.ratePercent())});
        terms.add(new String[]{"Μηνιαία δόση", money(d.monthlyPayment())});
        terms.add(new String[]{"Συνολικοί τόκοι", money(totalInterest)});
        terms.add(new String[]{"Συνολικό ποσό αποπληρωμής", money(totalRepayment)});
        terms.add(new String[]{"Λογαριασμός πίστωσης (IBAN)", IbanGenerator.format(d.iban())});
        p.keyValueTable(terms);

        // ---- Όροι
        p.heading("Γ. Όροι της σύμβασης");
        clause(p, "1. Αντικείμενο",
                "Η Τράπεζα χορηγεί στον Δανειολήπτη δάνειο ύψους " + money(d.amount()) + " για τον σκοπό «" + d.purpose()
                + "». Ο Δανειολήπτης δηλώνει ότι τα στοιχεία που έδωσε για τη χορήγηση του δανείου είναι αληθή και πλήρη.");
        clause(p, "2. Εκταμίευση",
                "Το ποσό του δανείου εκταμιεύεται εφάπαξ στις " + date + " με πίστωση του λογαριασμού με IBAN "
                + IbanGenerator.format(d.iban()) + ", που τηρείται στην Τράπεζα στο όνομα του Δανειολήπτη. "
                + "Με την πίστωση το ποσό τίθεται στη διάθεση του Δανειολήπτη.");
        clause(p, "3. Αποπληρωμή",
                "Ο Δανειολήπτης αποπληρώνει το δάνειο με " + months + " μηνιαίες δόσεις, καθεμία των "
                + money(d.monthlyPayment()) + ", οι οποίες περιλαμβάνουν κεφάλαιο και τόκους. Η τελευταία δόση "
                + "προσαρμόζεται ώστε να εξοφλείται ολόκληρη η οφειλή. Οι δόσεις καταβάλλονται με χρέωση του "
                + "λογαριασμού του Δανειολήπτη στην Τράπεζα ή με κατάθεση σε αυτήν. Αναλυτικός πίνακας αποπληρωμής "
                + "επισυνάπτεται στο Παράρτημα της παρούσας.");
        clause(p, "4. Επιτόκιο και κόστος",
                "Το δάνειο φέρει ετήσιο επιτόκιο " + String.format(GR, "%.2f%%", d.ratePercent()) + ". Ο τόκος υπολογίζεται "
                + "μηνιαία επί του εναπομένοντος κεφαλαίου. Το συνολικό ποσό που θα καταβάλει ο Δανειολήπτης, αν "
                + "εξυπηρετήσει κανονικά το δάνειο, ανέρχεται σε " + money(totalRepayment) + ", εκ των οποίων "
                + money(totalInterest) + " είναι τόκοι.");
        clause(p, "5. Καθυστέρηση",
                "Σε περίπτωση καθυστέρησης στην καταβολή δόσης, ο Δανειολήπτης οφείλει τόκους υπερημερίας σύμφωνα με "
                + "τον ισχύοντα Τιμοκατάλογο της Τράπεζας και τη νομοθεσία.");
        clause(p, "6. Πρόωρη εξόφληση",
                "Η ολική ή μερική πρόωρη εξόφληση του δανείου γίνεται σύμφωνα με τους όρους που ισχύουν στην Τράπεζα και "
                + "τη νομοθεσία.");
        clause(p, "7. Καταγγελία",
                "Η Τράπεζα δικαιούται να καταγγείλει τη σύμβαση και να ζητήσει την άμεση εξόφληση της οφειλής, σε περίπτωση "
                + "σοβαρής αθέτησης των υποχρεώσεων του Δανειολήπτη, σύμφωνα με τη νομοθεσία.");
        clause(p, "8. Προσωπικά δεδομένα",
                "Η Τράπεζα επεξεργάζεται τα προσωπικά δεδομένα του Δανειολήπτη για τη σύναψη και την εκτέλεση της "
                + "παρούσας σύμβασης, σύμφωνα με την ισχύουσα νομοθεσία για την προστασία των δεδομένων.");
        clause(p, "9. Εφαρμοστέο δίκαιο",
                "Η παρούσα σύμβαση διέπεται από το ελληνικό δίκαιο.");

        p.space(6);
        p.paragraph("Η σύμβαση συντάχθηκε σε δύο πρωτότυπα και κάθε συμβαλλόμενος έλαβε ένα. Εγκρίθηκε από: "
                + d.approvedBy() + ".", false, 10, Align.LEFT, 0, true);
        p.signatures("Για την Τράπεζα", d.isIndividual() ? "Ο Δανειολήπτης" : "Για την επιχείρηση");

        // ---- Παράρτημα
        p.newPage();
        p.heading("Παράρτημα: Πίνακας αποπληρωμής");
        p.paragraph("Σύμβαση #" + d.loanId() + " · " + d.name() + " · Ποσό " + money(d.amount()) + " · Επιτόκιο "
                + String.format(GR, "%.2f%%", d.ratePercent()), false, 9.5f, Align.LEFT, 0, true);
        p.space(4);

        List<String[]> rows = new ArrayList<>();
        for (AmortizationSchedule.Row r : schedule) {
            rows.add(new String[]{String.valueOf(r.number()), money(r.payment()), money(r.principal()),
                    money(r.interest()), money(r.balance())});
        }
        BigDecimal sumPrincipal = d.amount();
        rows.add(new String[]{"Σύνολο", money(totalRepayment), money(sumPrincipal), money(totalInterest), ""});

        float w = p.contentWidth();
        float[] widths = {w * 0.10f, w * 0.22f, w * 0.22f, w * 0.20f, w * 0.26f};
        Align[] aligns = {Align.CENTER, Align.RIGHT, Align.RIGHT, Align.RIGHT, Align.RIGHT};
        p.table(new String[]{"Δόση", "Ποσό δόσης", "Κεφάλαιο", "Τόκος", "Υπόλοιπο"}, rows, widths, aligns);

        return p.build();
    }

    private static void clause(PdfBuilder p, String title, String text) {
        p.ensureSpace(70);
        p.space(4);
        p.paragraph(title, true, 10.5f, Align.LEFT, 0, false);
        p.paragraph(text, false, 10.5f, Align.LEFT, 0, false);
    }

    private static String money(BigDecimal v) {
        return String.format(GR, "%,.2f €", v);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
