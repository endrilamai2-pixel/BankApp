package bank.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Μία κίνηση λογαριασμού. Οι αναλήψεις έχουν αρνητικό ποσό. */
public record TransactionRecord(LocalDateTime date, String type, BigDecimal amount,
                                BigDecimal balanceAfter, String description) {
}
