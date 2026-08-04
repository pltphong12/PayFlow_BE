CREATE TABLE transactions (
    id               UUID           PRIMARY KEY,
    type             VARCHAR(20)    NOT NULL,
    sender_user_id   UUID           NOT NULL,
    receiver_user_id UUID           NOT NULL,
    amount           DECIMAL(19, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    idempotency_key  VARCHAR(255)   NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT transactions_amount_positive
        CHECK (amount > 0),

    CONSTRAINT transactions_not_self_transfer
        CHECK (sender_user_id <> receiver_user_id),

    CONSTRAINT transactions_type_check
        CHECK (type IN ('TRANSFER')),

    CONSTRAINT transactions_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'COMPENSATING'))
);

CREATE INDEX idx_transactions_sender_user_id
    ON transactions (sender_user_id);

CREATE INDEX idx_transactions_receiver_user_id
    ON transactions (receiver_user_id);

CREATE INDEX idx_transactions_status_created_at
    ON transactions (status, created_at);


CREATE TABLE saga_steps (
    id             UUID         PRIMARY KEY,
    transaction_id UUID         NOT NULL REFERENCES transactions (id),
    step_name      VARCHAR(30)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    executed_at    TIMESTAMPTZ,

    CONSTRAINT saga_steps_name_check
        CHECK (step_name IN ('DEBIT_SENDER', 'CREDIT_RECEIVER')),

    CONSTRAINT saga_steps_status_check
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'COMPENSATED')),

    CONSTRAINT saga_steps_transaction_step_unique
        UNIQUE (transaction_id, step_name)
);

CREATE INDEX idx_saga_steps_transaction_id
    ON saga_steps (transaction_id);


CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at      TIMESTAMPTZ,

    CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'SENT'))
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (status, created_at);