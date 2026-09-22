CREATE TABLE merchants (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL UNIQUE,
    business_name       VARCHAR(160) NOT NULL,
    category            VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(64)  NOT NULL,
    bank_name           VARCHAR(120) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    rejected_reason     VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    approved_at         TIMESTAMPTZ,

    CONSTRAINT merchants_status_check
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),

    CONSTRAINT merchants_status_details_check CHECK (
        (
            status = 'PENDING_APPROVAL'
            AND approved_at IS NULL
            AND rejected_reason IS NULL
        )
        OR
        (
            status = 'APPROVED'
            AND approved_at IS NOT NULL
            AND rejected_reason IS NULL
        )
        OR
        (
            status = 'REJECTED'
            AND approved_at IS NULL
            AND NULLIF(BTRIM(rejected_reason), '') IS NOT NULL
        )
    )
);

CREATE INDEX idx_merchants_status_created_at
    ON merchants (status, created_at);


CREATE TABLE merchant_balances (
    id                UUID           PRIMARY KEY,
    merchant_id       UUID           NOT NULL UNIQUE,
    pending_balance   DECIMAL(19, 2) NOT NULL DEFAULT 0,
    settled_balance   DECIMAL(19, 2) NOT NULL DEFAULT 0,
    version           INTEGER        NOT NULL DEFAULT 0,

    CONSTRAINT fk_merchant_balances_merchant
        FOREIGN KEY (merchant_id)
        REFERENCES merchants (id)
        ON DELETE CASCADE,

    CONSTRAINT merchant_balances_pending_non_negative
        CHECK (pending_balance >= 0),

    CONSTRAINT merchant_balances_settled_non_negative
        CHECK (settled_balance >= 0),

    CONSTRAINT merchant_balances_version_non_negative
        CHECK (version >= 0)
);