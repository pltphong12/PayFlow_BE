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
