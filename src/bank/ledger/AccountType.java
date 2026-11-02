package bank.ledger;

/** Κατηγορία λογαριασμού. Καθορίζει την «κανονική» πλευρά του: τα στοιχεία ενεργητικού και τα έξοδα αυξάνονται με χρέωση. */
public enum AccountType {
    ASSET("Ενεργητικό", true),
    LIABILITY("Υποχρεώσεις", false),
    EQUITY("Ίδια κεφάλαια", false),
    INCOME("Έσοδα", false),
    EXPENSE("Έξοδα", true);

    public final String label;
    private final boolean debitNormal;

    AccountType(String label, boolean debitNormal) {
        this.label = label;
        this.debitNormal = debitNormal;
    }

    public boolean debitNormal() {
        return debitNormal;
    }
}
