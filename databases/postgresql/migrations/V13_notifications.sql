-- Customer notifications module (com.team5.notifications).
-- One row per notification: the delivery ledger and the customer's inbox in one table.
-- The title shown to the customer is derived from type, so it is not stored.

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    event_id        UUID         NOT NULL,   -- Kafka envelope eventId, or watchlist alertId
    account_id      BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    order_id        BIGINT,                  -- orders.order_id; null for a price alert
    message         VARCHAR(500) NOT NULL,
    channel         VARCHAR(10),             -- resolved from customer_preferences at send time
    status          VARCHAR(10)  NOT NULL DEFAULT 'QUEUED',
    last_error      VARCHAR(500),            -- why a FAILED send failed; null otherwise
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMPTZ,             -- when it went out; can be long after created_at if HELD
    read_at         TIMESTAMPTZ,             -- set when the customer opens it in the inbox

    -- idempotency: a replayed event inserts nothing
    CONSTRAINT uq_notifications_event_id UNIQUE (event_id),

    CONSTRAINT fk_notifications_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE,

    CONSTRAINT ck_notifications_type
        CHECK (type IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT')),

    CONSTRAINT ck_notifications_status
        CHECK (status IN ('QUEUED', 'HELD', 'SENT', 'FAILED', 'SKIPPED')),

    CONSTRAINT ck_notifications_channel
        CHECK (channel IS NULL OR channel IN ('EMAIL', 'PUSH', 'BROWSER', 'SMS', 'NONE'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_account_created
    ON notifications(account_id, created_at DESC);

-- the dispatcher only ever scans rows still waiting to go out
CREATE INDEX IF NOT EXISTS idx_notifications_pending
    ON notifications(created_at)
    WHERE status IN ('QUEUED', 'HELD');
