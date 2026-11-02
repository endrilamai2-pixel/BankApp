package bank.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Όλα τα στοιχεία που χρειάζονται για τη σύμβαση δανείου. Για επιχείρηση: idNumber = null, profession = δραστηριότητα. */
public record ContractData(long loanId, LocalDateTime date, int customerType, String name, String afm,
                           String idNumber, String profession, String address, String iban,
                           BigDecimal amount, int termYears, BigDecimal ratePercent,
                           BigDecimal monthlyPayment, BigDecimal totalRepayment,
                           String purpose, String approvedBy) {

    public boolean isIndividual() {
        return customerType == AccountDAO.TYPE_INDIVIDUAL;
    }
}
