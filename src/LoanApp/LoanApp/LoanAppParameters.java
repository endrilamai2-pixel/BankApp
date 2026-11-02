package LoanApp.LoanApp;

public final class LoanAppParameters {
    // ===================== ΠΑΡΑΜΕΤΡΟΙ =============================
    private LoanAppParameters() {}   // δεν δημιουργούνται αντικείμενα

    public static final double BASE_RATE = 4.0;        // βασικό επιτόκιο %
    public static final int MIN_SCORE_TO_APPLY = 500;  // κάτω από αυτό: αυτόματη απόρριψη
    public static final int MAX_TERM_YEARS = 30;

    /** Ελάχιστη κάλυψη εξυπηρέτησης χρέους για επιχειρήσεις: EBITDA / (σύνολο ετήσιων δόσεων). */
    public static final double MIN_DSCR = 1.25;
}
