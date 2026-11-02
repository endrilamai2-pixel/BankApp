package bank.account;

import java.math.BigDecimal;

/** Αποτέλεσμα πληρωμής δόσης δανείου. */
public record PaymentResult(BigDecimal paid, BigDecimal newBalance, int installmentNumber, int totalInstallments,
                            boolean loanClosed) { }
