# Loan Assessment App

A Java (Swing) desktop application that evaluates loan applications from
individuals and businesses, built as a learning project. The user interface is in Greek.

## Features

### Individuals
- Automatic credit score (300–850) from payment history, credit utilization,
  credit history length, recent inquiries and credit mix
- Approval or rejection with an interest rate based on credit tier, assets and loan purpose
  (investment, personal use, leisure); very poor credit history is rejected automatically

### Businesses
- Size classification under the Greek Accounting Standards (Law 4308/2014, thresholds as amended
  by Law 5164/2024): micro, small, medium, large
- Business credit score from financial ratios (solvency, liquidity, profitability, interest coverage,
  payment behaviour) and a debt-service coverage (DSCR) check
- Automatic reading of financial statements from PDF, TXT or CSV files, with optional OCR for
  scanned PDFs (Apache PDFBox and Tesseract)
- Company lookup by tax ID (VAT number) through the AADE RgWsPublic2 service, with mock data for demos

## Requirements
- Java 17 or newer
- Optional: Apache PDFBox (and Bouncy Castle for encrypted PDFs) to read PDF statements
- Optional: Tesseract OCR with Greek language data (`ell`) for scanned PDFs
- Optional: AADE credentials (`AADE_USERNAME`, `AADE_PASSWORD`, `AADE_CALLED_BY`)

## Run
See the root `README.md` for setup (database and environment variables) and how to run the application.

## Status and limitations
- Educational project, not a real credit decision tool. Rates, limits and weights are illustrative assumptions.
- The AADE SOAP client is present in `bank.kyc`; verify its request/response against the current official WSDL before using live credentials.
- Greek OCR accuracy has not been verified.