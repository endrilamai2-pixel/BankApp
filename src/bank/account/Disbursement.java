package bank.account;

import java.math.BigDecimal;

/** Αποτέλεσμα εκταμίευσης: ο αριθμός δανείου, ο λογαριασμός που πιστώθηκε και το νέο υπόλοιπο. */
public record Disbursement(long loanId, String iban, BigDecimal newBalance) {
}
