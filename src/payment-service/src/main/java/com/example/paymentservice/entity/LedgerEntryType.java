package com.example.paymentservice.entity;

// Values of ledger.entry_type in database_schema.md (db_payment)
public enum LedgerEntryType {
    PREAUTH,
    PREAUTH_REVERSAL,
    CAPTURE,
    REFUND,
    ADJUSTMENT
}
