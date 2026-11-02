package LoanApp.LoanApp;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Διαβάζει το κείμενο ενός αρχείου οικονομικών καταστάσεων: PDF, TXT ή CSV.
 *
 * Για PDF χρειάζεται η βιβλιοθήκη Apache PDFBox στο classpath (δεν είναι μέρος της Java). Ο κώδικας τη φορτώνει
 * δυναμικά, ώστε το project να κάνει compile και χωρίς αυτήν· αν λείπει, εμφανίζεται σχετικό μήνυμα.
 * Υποστηρίζονται οι εκδόσεις PDFBox 1.8, 2.x και 3.x.
 */
public final class StatementLoader {
    private StatementLoader() {}

    /** Το κείμενο του αρχείου και αν προήλθε από OCR (σκαναρισμένο PDF), οπότε είναι λιγότερο αξιόπιστο. */
    public record Loaded(String text, boolean usedOcr) {}

    public static String readText(File file) {
        return read(file).text();
    }

    /**
     * PDF με κείμενο: ανάγνωση απευθείας. PDF χωρίς κείμενο (σκαναρισμένο): αυτόματη προσπάθεια με OCR.
     * TXT/CSV: ανάγνωση ως κείμενο.
     */
    public static Loaded read(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".pdf")) {
                String text = extractPdfText(file);
                if (!text.isBlank()) return new Loaded(text, false);
                return new Loaded(PdfOcr.recognize(file), true);
            }
            if (name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".tsv")) {
                return new Loaded(decode(Files.readAllBytes(file.toPath())), false);
            }
        } catch (IOException e) {
            throw new StatementException("Αδυναμία ανάγνωσης του αρχείου: " + e.getMessage(), e);
        }
        throw new StatementException("Μη υποστηριζόμενος τύπος αρχείου. Χρησιμοποιήστε PDF, TXT ή CSV.");
    }

    /** UTF-8, με εναλλακτική την παλιά ελληνική κωδικοποίηση Windows-1253 (συχνή σε εξαγωγές λογιστικών προγραμμάτων). */
    static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("windows-1253"));
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    // ===================== PDF (μέσω Apache PDFBox, με reflection) =====================

    static String extractPdfText(File pdf) {
        Object doc = null;
        try {
            doc = loadDocument(pdf);
            unlockIfEncrypted(doc);
            Class<?> stripperClass = firstExisting("org.apache.pdfbox.text.PDFTextStripper",    // 2.x / 3.x
                    "org.apache.pdfbox.util.PDFTextStripper");   // 1.8
            Object stripper = stripperClass.getConstructor().newInstance();
            try {   // ταξινόμηση κατά θέση, ώστε ετικέτα και ποσά να βγαίνουν στην ίδια γραμμή
                stripperClass.getMethod("setSortByPosition", boolean.class).invoke(stripper, true);
            } catch (NoSuchMethodException ignored) {
            }
            Class<?> docClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            String text = (String) stripperClass.getMethod("getText", docClass).invoke(stripper, doc);
            return text == null ? "" : text;
        } catch (ClassNotFoundException e) {
            throw new StatementException("Για ανάγνωση PDF χρειάζεται η βιβλιοθήκη Apache PDFBox. "
                    + "Προσθέστε τα jar του PDFBox (pdfbox, fontbox, commons-logging) στο project, "
                    + "ή αποθηκεύστε τις καταστάσεις ως TXT/CSV.", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof NoClassDefFoundError) {
                throw new StatementException(missingLibraryMessage(cause), cause);
            }
            if (String.valueOf(cause.getMessage()).toLowerCase().contains("permission")) {
                throw new StatementException("Ο εκδότης του PDF δεν επιτρέπει την εξαγωγή κειμένου και η βιβλιοθήκη "
                        + "αρνείται την ανάγνωση. Χρησιμοποιήστε έκδοση του εγγράφου χωρίς περιορισμούς "
                        + "ή τα στοιχεία σε TXT/CSV.", cause);
            }
            if (cause.getClass().getSimpleName().contains("InvalidPassword")) {
                throw new StatementException("Το PDF προστατεύεται με κωδικό πρόσβασης και δεν μπορεί να διαβαστεί.", cause);
            }
            throw new StatementException("Αδυναμία ανάγνωσης του PDF: " + cause.getMessage(), cause);
        } catch (NoClassDefFoundError e) {
            throw new StatementException(missingLibraryMessage(e), e);
        } catch (ReflectiveOperationException e) {
            throw new StatementException("Μη συμβατή έκδοση του PDFBox: " + e.getMessage(), e);
        } finally {
            if (doc instanceof AutoCloseable c) {
                try { c.close(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Πολλά δημοσιευμένα PDF είναι «κρυπτογραφημένα» με ΚΕΝΟ κωδικό ανοίγματος (απλώς περιορίζουν την επεξεργασία).
     * Το PDFBox 1.8 τα ανοίγει χωρίς σελίδες αν δεν κληθεί decrypt(""). Οι εκδόσεις 2.x/3.x το κάνουν μόνες τους.
     * Αν ο κωδικός δεν είναι κενός, το αρχείο δεν διαβάζεται.
     */
    static void unlockIfEncrypted(Object doc) throws ReflectiveOperationException {
        Class<?> docClass = doc.getClass();
        if (!(boolean) docClass.getMethod("isEncrypted").invoke(doc)) return;
        try {
            docClass.getMethod("decrypt", String.class).invoke(doc, "");      // PDFBox 1.8
        } catch (NoSuchMethodException newerVersion) {
            // PDFBox 2.x/3.x: το έγγραφο έχει ήδη ανοιχτεί με κενό κωδικό
        }
    }

    private static String missingLibraryMessage(Throwable e) {
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("bouncycastle")) {
            return "Το PDF είναι κρυπτογραφημένο και χρειάζεται επιπλέον η βιβλιοθήκη Bouncy Castle (bcprov jar) στο project.";
        }
        return "Λείπει βιβλιοθήκη που απαιτεί το PDFBox: " + msg;
    }

    static Object loadDocument(File pdf) throws ReflectiveOperationException {
        try {                                                           // PDFBox 3.x: Loader.loadPDF(File)
            Class<?> loader = Class.forName("org.apache.pdfbox.Loader");
            return loader.getMethod("loadPDF", File.class).invoke(null, pdf);
        } catch (ClassNotFoundException notV3) {                        // PDFBox 1.8 / 2.x: PDDocument.load(File)
            Class<?> pd = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            return pd.getMethod("load", File.class).invoke(null, pdf);
        }
    }

    private static Class<?> firstExisting(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String n : names) {
            try { return Class.forName(n); } catch (ClassNotFoundException e) { last = e; }
        }
        throw last;
    }
}


