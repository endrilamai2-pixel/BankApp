package bank.account;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Desktop;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/** Δημιουργεί τη σύμβαση PDF ενός δανείου και την ανοίγει. */
public final class ContractService {

    private ContractService() { }

    /** Φάκελος αποθήκευσης: «BankContracts» μέσα στον προσωπικό φάκελο του χρήστη των Windows/Mac/Linux. */
    public static Path outputDir() {
        return Path.of(System.getProperty("user.home"), "BankContracts");
    }

    /** Δημιουργεί το PDF από τα στοιχεία της βάσης και επιστρέφει τη διαδρομή του. */
    public static Path createPdf(long loanId) throws Exception {
        ContractData data = LoanDAO.loadContractData(loanId);
        Path file = outputDir().resolve("Contract_" + loanId + "_" + data.afm() + ".pdf");
        LoanContractPdf.write(data, file);
        return file;
    }

    /** Δημιουργεί και ανοίγει τη σύμβαση, χωρίς να «παγώνει» η οθόνη. */
    public static void generateAndOpen(Component parent, long loanId) {
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return createPdf(loanId);
            }

            @Override
            protected void done() {
                try {
                    Path file = get();
                    boolean opened = false;
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(file.toFile());
                            opened = true;
                        }
                    } catch (Exception ignored) {
                        // θα εμφανιστεί η διαδρομή του αρχείου
                    }
                    if (!opened) {
                        JOptionPane.showMessageDialog(parent,
                                "Η σύμβαση αποθηκεύτηκε στο αρχείο:\n" + file, "Σύμβαση δανείου",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parent,
                            "Δεν δημιουργήθηκε η σύμβαση PDF (το δάνειο έχει ήδη πιστωθεί):\n" + cause.getMessage()
                            + "\n\nΜπορείς να τη δημιουργήσεις ξανά από την εφαρμογή «Λογαριασμοί πελατών» → καρτέλα Δάνεια.",
                            "Σφάλμα σύμβασης", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
