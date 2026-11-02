package bank.account;

import java.math.BigDecimal;

/** Όρια ασφαλείας για τις κινήσεις. Άλλαξε τις τιμές όπως θέλεις. */
public final class Limits {

    /** Μέγιστο συνολικό ποσό αναλήψεων + μεταφορών προς τα έξω ανά λογαριασμό και ημέρα. */
    public static final BigDecimal DAILY_OUTGOING_LIMIT = new BigDecimal("10000.00");

    /** Από αυτό το ποσό και πάνω ο υπάλληλος πρέπει να δώσει αιτιολογία, η οποία καταγράφεται στην κίνηση. */
    public static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("5000.00");

    private Limits() { }
}
