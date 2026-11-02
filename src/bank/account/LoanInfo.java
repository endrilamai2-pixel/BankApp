package bank.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ένα δάνειο πελάτη με την πρόοδο αποπληρωμής. */
public record LoanInfo(long id, LocalDateTime date, BigDecimal amount, String purpose, int termYears,
                       BigDecimal monthlyPayment, BigDecimal totalRepayment, int installmentsPaid,
                       BigDecimal paidTotal, String status) {

    public int months() { return termYears * 12; }

    public boolean isActive() { return "ACTIVE".equals(status == null ? "" : status.trim()); }

    public BigDecimal remaining() { return totalRepayment.subtract(paidTotal).max(BigDecimal.ZERO); }

    /** Το ποσό της επόμενης δόσης (η τελευταία δόση κλείνει ακριβώς την οφειλή). */
    public BigDecimal nextInstallment() {
        boolean last = installmentsPaid + 1 >= months();
        return last ? remaining() : monthlyPayment.min(remaining());
    }
}
