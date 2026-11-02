package bank.registration;

import bank.auth.Session;
import bank.kyc.AadeService;
import bank.kyc.KycChecker;
import bank.kyc.KycConfig;
import bank.kyc.KycDAO;
import bank.kyc.KycDialog;
import bank.ui.Dialogs;
import bank.ui.Forms;
import bank.ui.PlaceholderField;
import bank.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.util.List;
import java.util.function.Supplier;

/** Πάνελ εγγραφής νέων πελατών (ιδιώτης ή επιχείρηση). */
public class CustomerRegistrationPanel extends JPanel {

    private static final String CARD_INDIVIDUAL = "individual";
    private static final String CARD_BUSINESS = "business";

    private final CustomerDAO dao = new CustomerDAO();
    private final KycDAO kycDao = new KycDAO();
    private final AadeService aade = KycConfig.create();
    private final JButton saveButton = new JButton("Εγγραφή");
    private final JLabel statusLabel = new JLabel(" ");

    private final JRadioButton individualRadio = new JRadioButton("Ιδιώτης", true);
    private final JRadioButton businessRadio = new JRadioButton("Επιχείρηση");
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    // Ιδιώτης
    private final JTextField firstNameField = new PlaceholderField(26, "π.χ. Γιώργος");
    private final JTextField lastNameField = new PlaceholderField(26, "π.χ. Παπαδόπουλος");
    private final JTextField idNumberField = new PlaceholderField(26, "π.χ. ΑΕ 123456");
    private final JTextField indAfmField = new PlaceholderField(26, "9 ψηφία");
    private final JTextField professionField = new PlaceholderField(26, "π.χ. Μηχανικός");
    private final JTextField homeAddressField = new PlaceholderField(26, "Οδός, αριθμός, πόλη");
    private final JTextField dateOfBirthField = new PlaceholderField(26, "ΗΗ/ΜΜ/ΕΕΕΕ");
    private final JTextField phoneField = new PlaceholderField(26, "π.χ. 69XXXXXXXX");

    // Επιχείρηση
    private final JTextField companyNameField = new PlaceholderField(26, "π.χ. Άλφα Τρόφιμα Α.Ε.");
    private final JTextField headquartersField = new PlaceholderField(26, "Οδός, αριθμός, πόλη");
    private final JTextField bizAfmField = new PlaceholderField(26, "9 ψηφία");
    private final JTextField activityField = new PlaceholderField(26, "π.χ. Παραγωγή τροφίμων");

    public CustomerRegistrationPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        setBackground(Theme.SURFACE);
        JLabel title = Theme.title("Εγγραφή νέου πελάτη");
        JPanel heading = new JPanel(new GridLayout(2, 1));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(Theme.muted("Συμπλήρωσε τα στοιχεία. Το ΑΦΜ ελέγχεται στην ΑΑΔΕ πριν ολοκληρωθεί η εγγραφή."));
        add(heading, BorderLayout.NORTH);

        // Επιλογή τύπου πελάτη
        ButtonGroup group = new ButtonGroup();
        group.add(individualRadio);
        group.add(businessRadio);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        typePanel.setOpaque(false);
        individualRadio.setOpaque(false);
        businessRadio.setOpaque(false);
        cards.setOpaque(false);
        typePanel.add(new JLabel("Τύπος πελάτη:"));
        typePanel.add(individualRadio);
        typePanel.add(businessRadio);

        cards.add(buildIndividualForm(), CARD_INDIVIDUAL);
        cards.add(buildBusinessForm(), CARD_BUSINESS);

        individualRadio.addActionListener(e -> { cardLayout.show(cards, CARD_INDIVIDUAL); resetFeedback(); firstNameField.requestFocusInWindow(); });
        businessRadio.addActionListener(e -> { cardLayout.show(cards, CARD_BUSINESS); resetFeedback(); companyNameField.requestFocusInWindow(); });

        // Το μήνυμα κατάστασης μένει ακριβώς κάτω από τη φόρμα, κοντά στα πεδία που αφορά
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(typePanel, BorderLayout.NORTH);
        stack.add(cards, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        stack.add(statusLabel, BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(stack, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        // Κουμπιά
        JButton clearButton = new JButton("Καθαρισμός");
        saveButton.addActionListener(e -> onSave());
        clearButton.addActionListener(e -> clearFields());
        Theme.primary(saveButton);
        saveButton.setToolTipText("Ελέγχει το ΑΦΜ στην ΑΑΔΕ και καταχωρεί τον πελάτη");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        buttons.setOpaque(false);
        buttons.add(saveButton);
        buttons.add(clearButton);
        add(buttons, BorderLayout.SOUTH);

        setupBehaviour();
    }

    /** Συμπεριφορά φορμών: ψηφία στο ΑΦΜ, άμεσος έλεγχος, καθαρισμός λαθών στην πληκτρολόγηση, Enter, αρχική εστίαση. */
    private void setupBehaviour() {
        for (JTextField afm : new JTextField[]{indAfmField, bizAfmField}) {
            Forms.digitsOnly(afm, 9);
            Forms.onChange(afm, () -> liveAfmCheck(afm));
        }
        for (JTextField f : List.of(firstNameField, lastNameField, idNumberField, professionField, homeAddressField,
                companyNameField, headquartersField, activityField)) {
            ((AbstractDocument) f.getDocument()).setDocumentFilter(new UppercaseFilter());
            Forms.onChange(f, () -> clearMark(f));
        }
        Forms.dateField(dateOfBirthField);          // η κάθετος μπαίνει αυτόματα
        Forms.digitsOnly(phoneField, 10);           // μόνο ψηφία, το πολύ 10
        for (JTextField f : List.of(dateOfBirthField, phoneField)) Forms.onChange(f, () -> clearMark(f));
        Forms.enterMovesFocus(List.of(firstNameField, lastNameField, idNumberField, indAfmField, professionField,
                homeAddressField, dateOfBirthField, phoneField), this::onSave);
        Forms.enterMovesFocus(List.of(companyNameField, headquartersField, bizAfmField, activityField), this::onSave);
        Forms.focusWhenShown(this, firstNameField);
    }

    /** Άμεσος έλεγχος ΑΦΜ: μόλις συμπληρωθούν 9 ψηφία, ελέγχεται το ψηφίο ελέγχου. */
    private void liveAfmCheck(JTextField afm) {
        String v = afm.getText();
        if (v.length() == 9 && !isValidAfm(v)) {
            Theme.markInvalid(afm, true);
            status("Το ΑΦΜ δεν είναι έγκυρο (λάθος ψηφίο ελέγχου).", Theme.DANGER);
        } else {
            clearMark(afm);
        }
    }

    private void clearMark(JTextField f) {
        if (f.getClientProperty("bank.ui.originalBorder") != null || "error".equals(f.getClientProperty("JComponent.outline"))) {
            Theme.markInvalid(f, false);
            status(" ", Theme.MUTED);
        }
    }

    private void status(String text, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(text.isBlank() ? " " : text);
    }

    private void resetFeedback() {
        for (JTextField f : List.of(firstNameField, lastNameField, idNumberField, indAfmField, professionField,
                homeAddressField, dateOfBirthField, phoneField, companyNameField, headquartersField, bizAfmField, activityField)) {
            Theme.markInvalid(f, false);
        }
        status(" ", Theme.MUTED);
    }

    /** Επισημαίνει το πεδίο, εμφανίζει το μήνυμα κάτω από τη φόρμα και μεταφέρει εκεί τον κέρσορα. */
    private void fail(JTextField field, String message) {
        Theme.markInvalid(field, true);
        status(message, Theme.DANGER);
        field.requestFocusInWindow();
    }

    /** Επισημαίνει όλα τα κενά πεδία. Επιστρέφει true αν είναι όλα συμπληρωμένα. */
    private boolean requireFilled(JTextField... fields) {
        JTextField first = null;
        for (JTextField f : fields) {
            if (f.getText().trim().isEmpty()) {
                Theme.markInvalid(f, true);
                if (first == null) first = f;
            }
        }
        if (first == null) return true;
        status("Συμπλήρωσε τα πεδία που επισημαίνονται.", Theme.DANGER);
        first.requestFocusInWindow();
        return false;
    }

    private JPanel buildIndividualForm() {
        JPanel p = new JPanel(new GridBagLayout());
        addRow(p, 0, "Όνομα:", firstNameField);
        addRow(p, 1, "Επώνυμο:", lastNameField);
        addRow(p, 2, "Αριθμός Ταυτότητας:", idNumberField);
        addRow(p, 3, "ΑΦΜ:", afmWithCheck(indAfmField, false));
        addRow(p, 4, "Επάγγελμα:", professionField);
        addRow(p, 5, "Διεύθυνση Κατοικίας:", homeAddressField);
        addRow(p, 6, "Ημερομηνία Γέννησης:", dateOfBirthField);
        addRow(p, 7, "Τηλέφωνο:", phoneField);
        return wrapTop(p);
    }

    private JPanel buildBusinessForm() {
        JPanel p = new JPanel(new GridBagLayout());
        addRow(p, 0, "Επωνυμία:", companyNameField);
        addRow(p, 1, "Έδρα:", headquartersField);
        addRow(p, 2, "ΑΦΜ:", afmWithCheck(bizAfmField, true));
        addRow(p, 3, "Δραστηριότητα:", activityField);
        return wrapTop(p);
    }

    /** Η φόρμα μπαίνει σε λευκή κάρτα με λογικό πλάτος, στο πάνω μέρος της οθόνης (όχι τεντωμένη σε όλο το παράθυρο). */
    private JPanel wrapTop(JPanel form) {
        form.setOpaque(false);
        JPanel card = Theme.card(new BorderLayout());
        card.add(form, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        wrapper.setOpaque(false);
        wrapper.add(card);
        return wrapper;
    }

    private void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    // ------------------------------------------------------------------
    // Λογική αποθήκευσης
    // ------------------------------------------------------------------

    private void onSave() {
        try {
            if (individualRadio.isSelected()) {
                saveIndividual();
            } else {
                saveBusiness();
            }
        } catch (SQLException ex) {
            showError("Σφάλμα βάσης δεδομένων:\n" + ex.getMessage());
        }
    }

    private void saveIndividual() throws SQLException {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String idNumber = idNumberField.getText().trim();
        String afm = indAfmField.getText().trim();
        String profession = professionField.getText().trim();
        String address = homeAddressField.getText().trim();
        String phone = phoneField.getText().trim();
        LocalDate dateOfBirth;

        if (!requireFilled(firstNameField, lastNameField, idNumberField, indAfmField, professionField,
                homeAddressField, dateOfBirthField, phoneField)) {
            return;
        }
        try {
            dateOfBirth = LocalDate.parse(dateOfBirthField.getText().trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu"));
            if (dateOfBirth.isAfter(LocalDate.now())) throw new DateTimeParseException("future", dateOfBirthField.getText(), 0);
        } catch (DateTimeParseException ex) {
            fail(dateOfBirthField, "Η ημερομηνία πρέπει να έχει μορφή ΗΗ/ΜΜ/ΕΕΕΕ και να είναι έγκυρη.");
            return;
        }
        if (!phone.matches("\\d{10}")) {
            fail(phoneField, "Το τηλέφωνο πρέπει να έχει ακριβώς 10 ψηφία.");
            return;
        }
        if (!isValidAfm(afm)) {
            fail(indAfmField, "Το ΑΦΜ δεν είναι έγκυρο (9 ψηφία με σωστό ψηφίο ελέγχου).");
            return;
        }
        if (dao.afmExists(afm)) {
            fail(indAfmField, "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ.");
            return;
        }
        if (dao.idNumberExists(idNumber)) {
            fail(idNumberField, "Υπάρχει ήδη πελάτης με αυτόν τον αριθμό ταυτότητας.");
            return;
        }

        runKyc("INDIVIDUAL", afm, () -> KycChecker.checkIndividual(aade, afm, firstName, lastName), () -> {
            String iban = dao.saveIndividual(firstName, lastName, idNumber, afm, profession, address, dateOfBirth, phone);
            Dialogs.successWithCopy(this, "Επιτυχία",
                    "Ο πελάτης " + firstName + " " + lastName + " καταχωρήθηκε επιτυχώς.\n\nIBAN: "
                            + IbanGenerator.format(iban)
                            + "\nΑρ. λογαριασμού: " + IbanGenerator.accountNumber(iban), "Αντιγραφή IBAN", IbanGenerator.format(iban));
            clearFields();
        });
    }

    private void saveBusiness() throws SQLException {
        String name = companyNameField.getText().trim();
        String hq = headquartersField.getText().trim();
        String afm = bizAfmField.getText().trim();
        String activity = activityField.getText().trim();

        if (!requireFilled(companyNameField, headquartersField, bizAfmField, activityField)) {
            return;
        }
        if (!isValidAfm(afm)) {
            fail(bizAfmField, "Το ΑΦΜ δεν είναι έγκυρο (9 ψηφία με σωστό ψηφίο ελέγχου).");
            return;
        }
        if (dao.afmExists(afm)) {
            fail(bizAfmField, "Υπάρχει ήδη πελάτης με αυτό το ΑΦΜ.");
            return;
        }

        runKyc("BUSINESS", afm, () -> KycChecker.checkBusiness(aade, afm, name), () -> {
            String iban = dao.saveBusiness(name, hq, afm, activity);
            Dialogs.successWithCopy(this, "Επιτυχία",
                    "Η επιχείρηση \"" + name + "\" καταχωρήθηκε επιτυχώς.\n\nIBAN: "
                            + IbanGenerator.format(iban)
                            + "\nΑρ. λογαριασμού: " + IbanGenerator.accountNumber(iban), "Αντιγραφή IBAN", IbanGenerator.format(iban));
            clearFields();
        });
    }

    // ------------------------------------------------------------------
    // Έλεγχος KYC μέσω ΑΑΔΕ
    // ------------------------------------------------------------------

    /** Ενέργεια εγγραφής στη βάση, που εκτελείται αν περάσει ο έλεγχος KYC ή αν ο υπάλληλος επιλέξει εγγραφή παρ' όλα αυτά. */
    private interface Persist {
        void run() throws SQLException;
    }

    /** Πεδίο ΑΦΜ με κουμπί «Έλεγχος ΑΑΔΕ» δίπλα του. */
    private JPanel afmWithCheck(JTextField field, boolean business) {
        JButton check = new JButton("Έλεγχος ΑΑΔΕ");
        check.setToolTipText("Επαλήθευση του ΑΦΜ στην ΑΑΔΕ" + (business ? " και αυτόματη συμπλήρωση των κενών πεδίων" : ""));
        check.addActionListener(e -> onCheckOnly(business, check));
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(field, BorderLayout.CENTER);
        p.add(check, BorderLayout.EAST);
        return p;
    }

    /** Έλεγχος χωρίς εγγραφή. Για επιχείρηση συμπληρώνει τα κενά πεδία από την ΑΑΔΕ. */
    private void onCheckOnly(boolean business, JButton button) {
        String afm = (business ? bizAfmField : indAfmField).getText().trim();
        if (!isValidAfm(afm)) {
            fail(business ? bizAfmField : indAfmField, "Το ΑΦΜ δεν είναι έγκυρο (9 ψηφία με σωστό ψηφίο ελέγχου).");
            return;
        }
        String first = firstNameField.getText().trim();
        String last = lastNameField.getText().trim();
        String company = companyNameField.getText().trim();
        Supplier<KycChecker.Outcome> job = business
                ? () -> KycChecker.checkBusiness(aade, afm, company)
                : () -> KycChecker.checkIndividual(aade, afm, first, last);
        button.setEnabled(false);
        new SwingWorker<KycChecker.Outcome, Void>() {
            @Override protected KycChecker.Outcome doInBackground() { return job.get(); }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    KycChecker.Outcome o = get();
                    if (business && o.record() != null && o.record().isActiveAfm()) fillBusinessFields(o);
                    KycDialog.show(CustomerRegistrationPanel.this, o);
                } catch (Exception ex) {
                    showError("Σφάλμα ελέγχου ΑΑΔΕ:\n" + ex.getMessage());
                }
            }
        }.execute();
    }

    private void fillBusinessFields(KycChecker.Outcome o) {
        var r = o.record();
        if (companyNameField.getText().isBlank()) companyNameField.setText(r.name());
        if (headquartersField.getText().isBlank()) headquartersField.setText(r.address());
        var a = r.primaryActivity();
        if (activityField.getText().isBlank() && a != null) activityField.setText(a.description());
    }

    /**
     * Τρέχει τον έλεγχο ΑΑΔΕ σε άλλο νήμα (για να μην παγώνει το παράθυρο) και αποφασίζει:
     * απορρίφθηκε → δεν εγγράφεται· προειδοποίηση ή αδυναμία ελέγχου → ο υπάλληλος επιβεβαιώνει· επαληθεύτηκε → εγγραφή.
     * Κάθε έλεγχος καταγράφεται στον πίνακα kyc_checks.
     */
    private void runKyc(String customerType, String afm, Supplier<KycChecker.Outcome> check, Persist persist) {
        saveButton.setEnabled(false);
        status("Έλεγχος στην ΑΑΔΕ…", Theme.MUTED);
        new SwingWorker<KycChecker.Outcome, Void>() {
            @Override protected KycChecker.Outcome doInBackground() { return check.get(); }
            @Override protected void done() {
                try {
                    KycChecker.Outcome outcome;
                    try {
                        outcome = get();
                    } catch (Exception ex) {
                        // Ο έλεγχος απέτυχε εντελώς: ο υπάλληλος μπορεί και πάλι να επιλέξει εγγραφή με εκκρεμή KYC
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        outcome = new KycChecker.Outcome(KycChecker.Status.UNAVAILABLE, null,
                                java.util.List.of("Σφάλμα ελέγχου ΑΑΔΕ: " + cause.getMessage()), aade.sourceName());
                    }
                    handleKyc(customerType, afm, outcome, persist);
                } finally {
                    status(" ", Theme.MUTED);
                    saveButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void handleKyc(String customerType, String afm, KycChecker.Outcome o, Persist persist) {
        boolean register;
        if (o.status() == KycChecker.Status.VERIFIED) {
            register = true;
        } else {
            // Σε κάθε άλλη περίπτωση (προειδοποίηση, αδυναμία ελέγχου, απόρριψη) ο υπάλληλος μπορεί να επιλέξει
            // να γίνει εγγραφή έτσι κι αλλιώς. Ο έλεγχος καταγράφεται στο ιστορικό με το πραγματικό του αποτέλεσμα.
            String question;
            int messageType = JOptionPane.WARNING_MESSAGE;
            switch (o.status()) {
                case WARNING -> question = "Υπάρχουν ασυμφωνίες με την ΑΑΔΕ. Να γίνει εγγραφή παρ' όλα αυτά;";
                case REJECTED -> {
                    question = "Ο έλεγχος ΑΑΔΕ απέρριψε τα στοιχεία. Να γίνει εγγραφή παρ' όλα αυτά, με εκκρεμή έλεγχο KYC;";
                    messageType = JOptionPane.ERROR_MESSAGE;
                }
                default -> question = "Ο έλεγχος ΑΑΔΕ δεν ολοκληρώθηκε. Να γίνει εγγραφή με εκκρεμή έλεγχο KYC;";
            }
            String[] options = {"Εγγραφή παρ' όλα αυτά", "Ακύρωση"};
            // Στην απόρριψη προεπιλογή είναι η ακύρωση, ώστε να μην γίνει εγγραφή κατά λάθος με Enter
            Object defaultOption = o.status() == KycChecker.Status.REJECTED ? options[1] : options[0];
            int choice = JOptionPane.showOptionDialog(this,
                    new JLabel(KycDialog.html(o).replace("</body>", "<p><b>" + question + "</b></p></body>")),
                    "Έλεγχος ΑΑΔΕ (KYC)", JOptionPane.YES_NO_OPTION, messageType, null, options, defaultOption);
            register = choice == 0;
        }

        boolean registered = false;
        if (register) {
            try {
                persist.run();
                registered = true;
            } catch (SQLException ex) {
                showError("Σφάλμα βάσης δεδομένων:\n" + ex.getMessage());
            }
        }
        try {
            kycDao.log(afm, customerType, o, aade.isLive(), Session.currentUser(), registered);
        } catch (SQLException ex) {
            showError("Ο πελάτης " + (registered ? "εγγράφηκε αλλά" : "") + " ο έλεγχος KYC δεν καταγράφηκε στο ιστορικό:\n"
                    + ex.getMessage());
        }
    }

    /** Έλεγχος ΑΦΜ: 9 ψηφία + έλεγχος ψηφίου ελέγχου (αλγόριθμος ΑΑΔΕ). */
    private boolean isValidAfm(String afm) {
        if (!afm.matches("\\d{9}") || afm.equals("000000000")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += Character.getNumericValue(afm.charAt(i)) * (1 << (8 - i));
        }
        int check = (sum % 11) % 10;
        return check == Character.getNumericValue(afm.charAt(8));
    }

    private void clearFields() {
        for (JTextField f : new JTextField[] {
                firstNameField, lastNameField, idNumberField, indAfmField,
                professionField, homeAddressField, dateOfBirthField, phoneField,
                companyNameField, headquartersField, bizAfmField, activityField }) {
            f.setText("");
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Σφάλμα", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Επιτυχία", JOptionPane.INFORMATION_MESSAGE);
    }
    /** Μετατρέπει κάθε κείμενο της φόρμας σε κεφαλαία κατά την πληκτρολόγηση. */
    private static final class UppercaseFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String upper = text == null ? "" : text.toUpperCase(java.util.Locale.ROOT);
            fb.replace(offset, length, upper, attrs);
        }
    }
}
