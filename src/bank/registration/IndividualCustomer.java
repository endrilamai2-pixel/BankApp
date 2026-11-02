package bank.registration;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Πελάτης ιδιώτης, όπως είναι αποθηκευμένος στη βάση. */
public record IndividualCustomer(int id, String firstName, String lastName, String idNumber,
                                 String afm, String profession, String homeAddress,
                                 LocalDate dateOfBirth, String phone, String iban, BigDecimal balance) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String formattedDateOfBirth() {
        return dateOfBirth == null ? "—" : dateOfBirth.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
