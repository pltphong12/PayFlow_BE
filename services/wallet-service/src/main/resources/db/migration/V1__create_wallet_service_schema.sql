CREATE TABLE wallets (
    id         UUID           PRIMARY KEY,
    user_id    UUID           NOT NULL UNIQUE,
    balance    DECIMAL(19, 2) NOT NULL DEFAULT 0,
    currency   VARCHAR(3)     NOT NULL DEFAULT 'VND',
    version    INT            NOT NULL DEFAULT 0,
    status     VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT wallets_status_check CHECK (status IN ('ACTIVE', 'FROZEN'))
);
CREATE INDEX idx_wallets_user_id ON wallets (user_id);
CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY,
    wallet_id      UUID           NOT NULL REFERENCES wallets (id),
    transaction_id UUID,
    entry_type     VARCHAR(10)    NOT NULL,
    amount         DECIMAL(19, 2) NOT NULL,
    balance_after  DECIMAL(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_entries_type_check CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entries_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_ledger_entries_wallet_id ON ledger_entries (wallet_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);