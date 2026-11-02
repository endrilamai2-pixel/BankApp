package bank.account;

import java.math.BigDecimal;

/** Αποτέλεσμα μεταφοράς: νέο υπόλοιπο αποστολέα και στοιχεία δικαιούχου. */
public record TransferResult(BigDecimal newBalance, String destName, String destIban) { }
