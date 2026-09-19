ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_aggregate_event_unique
        UNIQUE (aggregate_id, event_type);
