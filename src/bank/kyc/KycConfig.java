package bank.kyc;

/**
 * Επιλέγει την υπηρεσία ΑΑΔΕ.
 * Αν οριστούν και τα τρία (μεταβλητές περιβάλλοντος ή -D ιδιότητες) χρησιμοποιείται η πραγματική υπηρεσία,
 * αλλιώς τα δοκιμαστικά δεδομένα:
 *   AADE_USERNAME  / -Daade.username   όνομα χρήστη ειδικών κωδικών πρόσβασης
 *   AADE_PASSWORD  / -Daade.password   κωδικός
 *   AADE_CALLED_BY / -Daade.calledby   ΑΦΜ του φορέα (της τράπεζας) που κάνει την κλήση
 *   AADE_ENDPOINT  / -Daade.endpoint   (προαιρετικό) διαφορετικό URL υπηρεσίας
 */
public final class KycConfig {
    private KycConfig() { }

    public static AadeService create() {
        String user = read("AADE_USERNAME", "aade.username");
        String pass = read("AADE_PASSWORD", "aade.password");
        String by = read("AADE_CALLED_BY", "aade.calledby");
        if (user == null || pass == null || by == null) return new MockAadeService();
        String endpoint = read("AADE_ENDPOINT", "aade.endpoint");
        return endpoint == null
                ? new AadeRegistryClient(user, pass, by)
                : new AadeRegistryClient(user, pass, by, endpoint);
    }

    private static String read(String env, String prop) {
        String v = System.getenv(env);
        if (v == null || v.isBlank()) v = System.getProperty(prop);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
