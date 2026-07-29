CREATE TABLE topup_requests (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    idempotency_key VARCHAR(255)   NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT topup_requests_amount_positive CHECK (amount > 0),
    CONSTRAINT topup_requests_status_check
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX idx_topup_requests_user_id
    ON topup_requests (user_id);
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