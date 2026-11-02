package bank.account;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Πίνακας αποπληρωμής αποσβεστικού δανείου (ισόποσες μηνιαίες δόσεις). */
public final class AmortizationSchedule {

    public record Row(int number, BigDecimal payment, BigDecimal principal, BigDecimal interest, BigDecimal balance) { }

    private AmortizationSchedule() { }

    /** Η τελευταία δόση προσαρμόζεται ώστε το υπόλοιπο να μηδενίζεται ακριβώς. */
    public static List<Row> build(BigDecimal amount, BigDecimal annualRatePercent, int months, BigDecimal payment) {
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MathContext.DECIMAL64);
        BigDecimal balance = amount;
        List<Row> rows = new ArrayList<>();
        for (int n = 1; n <= months; n++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal;
            BigDecimal pay;
            if (n == months) {
                principal = balance;
                pay = principal.add(interest);
            } else {
                principal = payment.subtract(interest).min(balance);
                pay = principal.add(interest);
            }
            balance = balance.subtract(principal);
            rows.add(new Row(n, pay, principal, interest, balance));
        }
        return rows;
    }

    public static BigDecimal total(List<Row> rows) {
        BigDecimal t = BigDecimal.ZERO;
        for (Row r : rows) t = t.add(r.payment());
        return t;
    }
}
