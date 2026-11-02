package bank.auth;

/** Ο χρήστης που είναι συνδεδεμένος αυτή τη στιγμή (χρησιμοποιείται στο ιστορικό των εκταμιεύσεων). */
public final class Session {

    private static volatile String currentUser;

    private Session() { }

    public static void setUser(String username) { currentUser = username; }

    public static String currentUser() { return currentUser != null ? currentUser : "άγνωστος"; }
}
