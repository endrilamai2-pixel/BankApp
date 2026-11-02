package bank.customers;

import bank.registration.BusinessCustomer;
import bank.registration.CustomerDAO;
import bank.registration.CustomerInfoDialog;
import bank.registration.IbanGenerator;
import bank.registration.IndividualCustomer;
import bank.ui.Theme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Λίστα όλων των πελατών, μόνο για ανάγνωση: φίλτρο τύπου, αναζήτηση με όνομα/ΑΦΜ/IBAN, επισήμανση εκκρεμούς KYC.
 * Στον πίνακα φαίνονται μόνο τα βασικά στοιχεία· τα πλήρη στοιχεία ανοίγουν με διπλό κλικ.
 */
public final class CustomersPanel extends JPanel {

    private static final String[] COLUMNS = {"Τύπος", "Όνομα / Επωνυμία", "ΑΦΜ", "IBAN", "Αρ. λογαριασμού", "Υπόλοιπο",
            "Ενεργά δάνεια", "KYC", "Εγγραφή"};
    private static final int C_BALANCE = 5, C_LOANS = 6, C_KYC = 7;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CustomerListDAO dao = new CustomerListDAO();
    private final CustomerDAO customerDao = new CustomerDAO();
    private final List<CustomerListDAO.Row> rows = new ArrayList<>();

    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final TableRowSorter<Model> sorter = new TableRowSorter<>(model);
    private final JTextField searchField = new JTextField(22);
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"Όλοι", "Ιδιώτες", "Επιχειρήσεις"});
    private final JCheckBox pendingOnly = new JCheckBox("Μόνο με εκκρεμές KYC");
    private final JButton refreshBtn = new JButton("Ανανέωση");
    private final JLabel info = new JLabel(" ");
    private boolean loading;

    public CustomersPanel() {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setPreferredSize(new Dimension(1050, 560));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(Theme.title("Πελάτες"), BorderLayout.NORTH);
        top.add(controls(), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        Theme.styleTable(table, C_BALANCE, C_LOANS);
        table.getColumnModel().getColumn(C_BALANCE).setCellRenderer(new MoneyRenderer());
        table.getColumnModel().getColumn(C_KYC).setCellRenderer(new KycRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {90, 220, 90, 230, 110, 110, 90, 170, 90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDetails();
            }
        });
        table.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "details");
        table.getActionMap().put("details", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showDetails(); }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        info.setForeground(Theme.MUTED);
        bottom.add(info, BorderLayout.WEST);
        bottom.add(Theme.muted("Διπλό κλικ ή Enter για τα πλήρη στοιχεία"), BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        reload();
    }

    private JPanel controls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchField.putClientProperty("JTextField.placeholderText", "Αναζήτηση: όνομα, ΑΦΜ, IBAN ή αρ. λογαριασμού");
        searchField.setToolTipText("Αναζήτηση με όνομα/επωνυμία, ΑΦΜ, IBAN ή αριθμό λογαριασμού (10 τελευταία ψηφία του IBAN)");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        typeFilter.addActionListener(e -> applyFilter());
        pendingOnly.addActionListener(e -> applyFilter());
        pendingOnly.setToolTipText("Πελάτες χωρίς ολοκληρωμένο έλεγχο KYC (εκκρεμής, απορρίφθηκε ή χωρίς έλεγχο)");
        refreshBtn.addActionListener(e -> reload());
        p.add(new JLabel("Αναζήτηση:"));
        p.add(searchField);
        p.add(typeFilter);
        p.add(pendingOnly);
        p.add(refreshBtn);
        return p;
    }

    // ------------------------------------------------------------------ φόρτωση

    /** Φορτώνει ξανά τη λίστα από τη βάση (σε άλλο νήμα). Αν ήδη φορτώνει, δεν κάνει τίποτα. */
    public void reload() {
        if (loading) return;
        loading = true;
        refreshBtn.setEnabled(false);
        info.setForeground(Theme.MUTED);
        info.setText("Φόρτωση...");
        new SwingWorker<List<CustomerListDAO.Row>, Void>() {
            @Override protected List<CustomerListDAO.Row> doInBackground() throws SQLException {
                return dao.listAll();
            }
            @Override protected void done() {
                loading = false;
                refreshBtn.setEnabled(true);
                try {
                    rows.clear();
                    rows.addAll(get());
                    model.fireTableDataChanged();
                    updateInfo();
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    info.setForeground(Theme.DANGER);
                    info.setText("Σφάλμα βάσης: " + t.getMessage());
                }
            }
        }.execute();
    }

    private void updateInfo() {
        long ind = rows.stream().filter(r -> !r.business()).count();
        long biz = rows.size() - ind;
        long follow = rows.stream().filter(r -> r.kyc().needsFollowUp()).count();
        String shown = table.getRowCount() == rows.size() ? "" : "Εμφανίζονται " + table.getRowCount() + " από ";
        info.setForeground(Theme.MUTED);
        info.setText(shown + rows.size() + " πελάτες (" + ind + " ιδιώτες, " + biz + " επιχειρήσεις) · "
                + follow + " με KYC υπό παρακολούθηση");
    }

    // ------------------------------------------------------------------ φίλτρα

    private void applyFilter() {
        String q = normalize(searchField.getText()).replace(" ", "");
        int type = typeFilter.getSelectedIndex();       // 0 όλοι, 1 ιδιώτες, 2 επιχειρήσεις
        boolean pending = pendingOnly.isSelected();
        sorter.setRowFilter(new RowFilter<Model, Integer>() {
            @Override public boolean include(Entry<? extends Model, ? extends Integer> entry) {
                CustomerListDAO.Row r = rows.get(entry.getIdentifier());
                if (type == 1 && r.business()) return false;
                if (type == 2 && !r.business()) return false;
                if (pending && !r.kyc().needsFollowUp()) return false;
                if (q.isEmpty()) return true;
                return normalize(r.name()).replace(" ", "").contains(q)
                        || r.afm().contains(q)
                        || r.iban().replace(" ", "").toLowerCase(Locale.ROOT).contains(q);
            }
        });
        updateInfo();
    }

    /** Πεζά και χωρίς τόνους, ώστε το «γιωργος» να βρίσκει το «Γιώργος». */
    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('ς', 'σ');
    }

    // ------------------------------------------------------------------ λεπτομέρειες

    private void showDetails() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        CustomerListDAO.Row r = rows.get(table.convertRowIndexToModel(viewRow));
        try {
            if (r.business()) {
                BusinessCustomer c = customerDao.findBusinessByAfm(r.afm()).orElse(null);
                if (c == null) notFound(); else CustomerInfoDialog.showBusiness(this, c, "Στοιχεία πελάτη");
            } else {
                IndividualCustomer c = customerDao.findIndividualByAfm(r.afm()).orElse(null);
                if (c == null) notFound(); else CustomerInfoDialog.showIndividual(this, c, "Στοιχεία πελάτη");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Σφάλμα βάσης δεδομένων:\n" + ex.getMessage(),
                    "Σφάλμα", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void notFound() {
        JOptionPane.showMessageDialog(this, "Ο πελάτης δεν βρέθηκε πλέον. Πάτα «Ανανέωση».",
                "Πελάτες", JOptionPane.WARNING_MESSAGE);
        reload();
    }

    // ------------------------------------------------------------------ πίνακας

    private final class Model extends AbstractTableModel {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case C_BALANCE -> BigDecimal.class;
                case C_LOANS -> Integer.class;
                default -> String.class;
            };
        }

        @Override public Object getValueAt(int row, int c) {
            CustomerListDAO.Row r = rows.get(row);
            return switch (c) {
                case 0 -> r.typeLabel();
                case 1 -> r.name();
                case 2 -> r.afm();
                case 3 -> r.iban().isBlank() ? "—" : IbanGenerator.format(r.iban());
                case 4 -> IbanGenerator.accountNumber(r.iban());
                case C_BALANCE -> r.balance();
                case C_LOANS -> r.activeLoans();
                case C_KYC -> r.kyc().label;
                default -> r.registeredAt() == null ? "—" : r.registeredAt().format(DATE);
            };
        }
    }

    private static class MoneyRenderer extends DefaultTableCellRenderer {
        MoneyRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus,
                                                                 int row, int col) {
            String text = v instanceof BigDecimal b
                    ? String.format(Locale.forLanguageTag("el-GR"), "%,.2f €", b) : String.valueOf(v);
            super.getTableCellRendererComponent(t, text, sel, false, row, col);
            if (!sel) setBackground(row % 2 == 0 ? t.getBackground() : Theme.ZEBRA);
            return this;
        }
    }

    /** Χρώμα στην κατάσταση KYC: πράσινο όταν επαληθεύτηκε, πορτοκαλί σε εκκρεμότητα, κόκκινο σε απόρριψη. */
    private final class KycRenderer extends DefaultTableCellRenderer {
        KycRenderer() { setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8)); }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus,
                                                                 int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            if (!sel) {
                setBackground(row % 2 == 0 ? t.getBackground() : Theme.ZEBRA);
                CustomerListDAO.Row r = rows.get(t.convertRowIndexToModel(row));
                setForeground(switch (r.kyc()) {
                    case VERIFIED -> Theme.SUCCESS;
                    case REJECTED -> Theme.DANGER;
                    case PENDING -> Theme.WARNING;
                    case NONE -> Theme.MUTED;
                });
            }
            return this;
        }
    }
}
