-- Δημιουργία της βάσης και του χρήστη για την εφαρμογή (Microsoft SQL Server).
-- Τρέξε το με διαχειριστικό λογαριασμό (π.χ. sa) και ΑΛΛΑΞΕ τον κωδικό πριν το εκτελέσεις.
-- Οι πίνακες δημιουργούνται αυτόματα από την εφαρμογή στην πρώτη εκκίνηση.

IF DB_ID('BankDB') IS NULL
    CREATE DATABASE BankDB;
GO

IF SUSER_ID('bankuser') IS NULL
    CREATE LOGIN bankuser WITH PASSWORD = 'ChangeMe!12345', CHECK_POLICY = ON;   -- ΑΛΛΑΞΕ ΤΟΝ ΚΩΔΙΚΟ
GO

USE BankDB;
GO

IF USER_ID('bankuser') IS NULL
    CREATE USER bankuser FOR LOGIN bankuser;
GO

-- Η εφαρμογή δημιουργεί πίνακες και στήλες, άρα χρειάζεται δικαιώματα σχήματος στη βάση
ALTER ROLE db_owner ADD MEMBER bankuser;
GO
