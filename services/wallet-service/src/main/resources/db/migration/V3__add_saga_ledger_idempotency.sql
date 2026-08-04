CREATE UNIQUE INDEX uq_ledger_entries_wallet_transaction_entry_type
    ON ledger_entries (wallet_id, transaction_id, entry_type)
    WHERE transaction_id IS NOT NULL;