package bank.registration;

import java.math.BigDecimal;

/** Πελάτης επιχείρηση, όπως είναι αποθηκευμένος στη βάση. */
public record BusinessCustomer(int id, String companyName, String headquarters,
                               String afm, String activity, String iban, BigDecimal balance) {
}
