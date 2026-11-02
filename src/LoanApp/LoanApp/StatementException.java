package LoanApp.LoanApp;


        /** Σφάλμα κατά την ανάγνωση αρχείου οικονομικών καταστάσεων. */
        public class StatementException extends RuntimeException {
            public StatementException(String message) { super(message); }
            public StatementException(String message, Throwable cause) { super(message, cause); }
        }


