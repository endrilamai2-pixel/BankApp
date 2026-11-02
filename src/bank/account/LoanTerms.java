package bank.account;

import java.math.BigDecimal;

/** Οι όροι ενός εγκεκριμένου δανείου, όπως θα καταχωρηθούν κατά την εκταμίευση. */
public record LoanTerms(BigDecimal amount, int termYears, double ratePercent,
                        double monthlyPayment, double totalCost, String purpose) {
}
