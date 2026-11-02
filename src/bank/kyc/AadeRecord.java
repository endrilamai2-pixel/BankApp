package bank.kyc;

import java.time.LocalDate;
import java.util.List;

/** Τα στοιχεία ενός ΑΦΜ όπως επιστρέφονται από την ΑΑΔΕ (υπηρεσία RgWsPublic2). */
public record AadeRecord(String afm, String name, String tradeTitle, String legalStatus,
                         boolean naturalPerson, String deactivationFlag, String deactivationDescr,
                         String firmFlagDescr, String taxOffice, String address,
                         LocalDate registrationDate, LocalDate stopDate,
                         List<Activity> activities) {

    /** Μία δραστηριότητα (ΚΑΔ). */
    public record Activity(String code, String description, boolean primary) { }

    /** Ενεργός ΑΦΜ: ο κωδικός «1» σημαίνει «ΕΝΕΡΓΟΣ ΑΦΜ». Οτιδήποτε άλλο θεωρείται ανενεργό. */
    public boolean isActiveAfm() {
        return "1".equals(deactivationFlag == null ? null : deactivationFlag.trim());
    }

    /** Έχει δηλωθεί παύση εργασιών (υπάρχει ημερομηνία παύσης). */
    public boolean hasStopped() {
        return stopDate != null;
    }

    public Activity primaryActivity() {
        for (Activity a : activities) {
            if (a.primary()) return a;
        }
        return activities.isEmpty() ? null : activities.get(0);
    }
}
