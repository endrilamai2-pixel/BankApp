package bank.ledger;

/**
 * Λογαριασμός του σχεδίου λογαριασμών. Πρωτοβάθμιος = κωδικός 2 ψηφίων (π.χ. "20"), δευτεροβάθμιος = "20.00".
 * Εγγραφές γίνονται μόνο σε δευτεροβάθμιους (φύλλα). Ο πρώτος αριθμός του κωδικού είναι η ομάδα.
 */
public record GlAccount(String code, String name, int group, AccountType type, String parentCode) {

    public boolean isLeaf() {
        return parentCode != null;
    }
}
