package LoanApp.LoanApp;


    /** Αίτηση δανείου από επιχείρηση. */
    public record BusinessApplication(String companyName,
                                      BusinessScoreCalculator.BusinessFinancials fin,
                                      int creditScore,
                                      EntityCategory category,
                                      double amount,
                                      int termYears,
                                      BusinessPurpose purpose) {}


