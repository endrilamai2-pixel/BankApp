package LoanApp.LoanApp;

import bank.kyc.AadeRecord;
import bank.kyc.AadeService;
import bank.kyc.KycConfig;

import java.util.Optional;

/**
 * Προσαρμογέας της υπηρεσίας ΑΑΔΕ στο μητρώο που χρησιμοποιεί η φόρμα δανείου.
 * Χωρίς διαπιστευτήρια χρησιμοποιούνται τα δοκιμαστικά δεδομένα της MockAadeService.
 */
public final class AadeCompanyRegistry implements CompanyRegistryService {
    private final AadeService aade;

    public AadeCompanyRegistry(AadeService aade) {
        this.aade = aade;
    }

    public static AadeCompanyRegistry create() {
        return new AadeCompanyRegistry(KycConfig.create());
    }

    @Override
    public Optional<CompanyRecord> findByTaxId(String taxId) {
        return aade.lookup(taxId).map(this::toCompanyRecord);
    }

    private CompanyRecord toCompanyRecord(AadeRecord record) {
        String status = record.isActiveAfm() && !record.hasStopped()
                ? "Ενεργός ΑΦΜ"
                : "Ανενεργός ΑΦΜ";

        return new CompanyRecord(
                record.afm(),
                record.name(),
                blankToNull(record.legalStatus()),
                status,
                record.registrationDate(),
                blankToNull(record.address())
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public String sourceName() {
        return aade.sourceName();
    }
}
