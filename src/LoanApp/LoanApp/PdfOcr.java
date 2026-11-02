package LoanApp.LoanApp;


    import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

    /**
     * OCR για σκαναρισμένα PDF (εικόνες χωρίς κείμενο): κάθε σελίδα μετατρέπεται σε εικόνα με το PDFBox και
     * διαβάζεται από το Tesseract OCR, που πρέπει να είναι εγκατεστημένο ξεχωριστά στον υπολογιστή.
     *
     * Ρυθμίσεις (προαιρετικές):
     *   -Dtesseract.cmd=C:\Program Files\Tesseract-OCR\tesseract.exe   (ή μεταβλητή περιβάλλοντος TESSERACT_CMD)
     *   -Dtesseract.lang=ell+eng                                       (προεπιλογή)
     *
     * Για καλή ανάγνωση ελληνικών χρειάζεται το αρχείο γλώσσας «ell.traineddata» του Tesseract.
     */
    final class PdfOcr {
        private PdfOcr() {}

        private static final int DPI = 300;

        static String recognize(File pdf) {
            Path dir = null;
            Object doc = null;
            try {
                dir = Files.createTempDirectory("statement-ocr");
                doc = StatementLoader.loadDocument(pdf);
                StatementLoader.unlockIfEncrypted(doc);

                int pages = (int) doc.getClass().getMethod("getNumberOfPages").invoke(doc);
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < pages; i++) {
                    Path png = dir.resolve("page-" + i + ".png");
                    ImageIO.write(renderPage(doc, i), "png", png.toFile());
                    text.append(runTesseract(png, dir)).append('\n');
                }
                return text.toString();
            } catch (ClassNotFoundException e) {
                throw new StatementException("Για OCR χρειάζεται η βιβλιοθήκη Apache PDFBox στο project.", e);
            } catch (InvocationTargetException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                throw new StatementException("Αδυναμία επεξεργασίας των σελίδων για OCR: " + c.getMessage(), c);
            } catch (IOException | ReflectiveOperationException e) {
                throw new StatementException("Αδυναμία OCR: " + e.getMessage(), e);
            } finally {
                if (doc instanceof AutoCloseable c) {
                    try { c.close(); } catch (Exception ignored) { }
                }
                deleteQuietly(dir);
            }
        }

        /** Μετατροπή σελίδας PDF σε εικόνα: PDFBox 2.x/3.x (PDFRenderer) ή 1.8 (PDPage.convertToImage). */
        private static BufferedImage renderPage(Object doc, int index) throws ReflectiveOperationException {
            Class<?> docClass = doc.getClass();
            try {
                Class<?> rendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");
                Object renderer = rendererClass.getConstructor(docClass).newInstance(doc);
                return (BufferedImage) rendererClass.getMethod("renderImageWithDPI", int.class, float.class)
                        .invoke(renderer, index, (float) DPI);
            } catch (ClassNotFoundException pdfbox1) {
                Object catalog = docClass.getMethod("getDocumentCatalog").invoke(doc);
                List<?> pages = (List<?>) catalog.getClass().getMethod("getAllPages").invoke(catalog);
                Object page = pages.get(index);
                return (BufferedImage) page.getClass().getMethod("convertToImage", int.class, int.class)
                        .invoke(page, BufferedImage.TYPE_BYTE_GRAY, DPI);
            }
        }

        private static String runTesseract(Path image, Path workDir) throws IOException {
            String cmd = System.getProperty("tesseract.cmd");
            if (cmd == null || cmd.isBlank()) cmd = System.getenv("TESSERACT_CMD");
            if (cmd == null || cmd.isBlank()) cmd = "tesseract";
            String lang = System.getProperty("tesseract.lang", "ell+eng");

            List<String> command = new ArrayList<>(List.of(cmd, image.toString(), "stdout", "-l", lang,
                    "--psm", "6", "-c", "preserve_interword_spaces=1"));
            Path errFile = workDir.resolve("tesseract-error.txt");
            Process process;
            try {
                process = new ProcessBuilder(command).redirectError(errFile.toFile()).start();
            } catch (IOException e) {
                throw new StatementException("Το PDF είναι σκαναρισμένο (εικόνα) και χρειάζεται OCR. Εγκαταστήστε το "
                        + "Tesseract OCR με τα ελληνικά (ell) ή ορίστε τη διαδρομή του με -Dtesseract.cmd=..., "
                        + "ή αποθηκεύστε τις καταστάσεις ως TXT/CSV.", e);
            }
            try {
                byte[] out = process.getInputStream().readAllBytes();
                if (!process.waitFor(180, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new StatementException("Το OCR καθυστέρησε υπερβολικά και διακόπηκε.");
                }
                String err = Files.exists(errFile) ? Files.readString(errFile, StandardCharsets.UTF_8) : "";
                // Με γλώσσες «ell+eng» το Tesseract συνεχίζει με όσες φόρτωσε και βγάζει ακατάληπτα ελληνικά ΧΩΡΙΣ σφάλμα:
                // γι' αυτό τα μηνύματα ελέγχονται πάντα, όχι μόνο όταν ο κωδικός εξόδου είναι μη μηδενικός.
                if (err.contains("Failed loading language") || err.contains("Error opening data file")) {
                    throw new StatementException("Λείπουν δεδομένα γλώσσας του Tesseract για «" + lang
                            + "». Εγκαταστήστε το ell.traineddata (ελληνικά).");
                }
                if (process.exitValue() != 0) {
                    throw new StatementException("Το Tesseract απέτυχε: " + err.lines().findFirst().orElse("άγνωστο σφάλμα"));
                }
                return new String(out, StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StatementException("Το OCR διακόπηκε.", e);
            }
        }

        private static void deleteQuietly(Path dir) {
            if (dir == null) return;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
            }
        }
    }


