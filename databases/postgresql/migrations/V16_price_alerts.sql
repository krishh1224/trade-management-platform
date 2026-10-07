-- Price alerts, owned by the Watchlists module in the Trade REST API (com.team5.watchlists).
--
-- These are the only tables the module adds. It also owns watchlists (V7) and watchlist_groups (V8);
-- every other table is read-only to it, and it never writes the notifications table (V13): a crossed
-- alert is handed to the notifications module through its Java interface AlertDelivery.
--
-- price_alerts: one row per alert. An alert hangs off a watchlist entry, so removing the entry removes
-- its alerts, and the executor (which only polls symbols someone holds or watches) keeps quoting it.
--
--   ACTIVE    -> evaluated against every market-data quote for its symbol
--   TRIGGERED -> crossed once, handed to notifications, never evaluated again (one-shot)
--
-- Crossed means, with "previous" the last price this module saw for the symbol:
--   ABOVE: previous <  threshold AND current >= threshold
--   BELOW: previous >  threshold AND current <= threshold
-- A price that stays beyond the threshold is not a crossing, so it cannot fire on every quote.

CREATE TABLE IF NOT EXISTS price_alerts (
    alert_id       BIGSERIAL     PRIMARY KEY,
    account_id     BIGINT        NOT NULL,
    watchlist_id   BIGINT        NOT NULL,
    symbol         VARCHAR(20)   NOT NULL,   -- copied from the entry's instrument: the per-quote lookup key
    direction      VARCHAR(5)    NOT NULL,
    threshold      DECIMAL(19,4) NOT NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    delivery_id    UUID          NOT NULL,   -- idempotency key handed to notifications: one message per alert
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triggered_at   TIMESTAMPTZ,
    trigger_price  DECIMAL(19,4),

    CONSTRAINT uq_price_alerts_delivery_id UNIQUE (delivery_id),

    CONSTRAINT fk_price_alerts_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_price_alerts_watchlist
        FOREIGN KEY (watchlist_id)
            REFERENCES watchlists(watchlist_id)
            ON DELETE CASCADE,

    CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE', 'BELOW')),

    CONSTRAINT ck_price_alerts_status
        CHECK (status IN ('ACTIVE', 'TRIGGERED')),

    CONSTRAINT ck_price_alerts_threshold
        CHECK (threshold > 0),

    -- a TRIGGERED row always says when and at what price; an ACTIVE one never does
    CONSTRAINT ck_price_alerts_triggered
        CHECK ((status = 'TRIGGERED') = (triggered_at IS NOT NULL AND trigger_price IS NOT NULL))
);

-- Every market-data quote looks up the ACTIVE alerts for its symbol whose threshold lies between the
-- previous and the current price. Triggered alerts are never looked at again, so they stay out of it.
CREATE INDEX IF NOT EXISTS idx_price_alerts_active_symbol
    ON price_alerts (symbol, threshold)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_price_alerts_account_created
    ON price_alerts (account_id, created_at DESC);

-- The last market-data price this module consumed per symbol: the "previous price" a crossing is
-- measured from, and the live price shown beside a watched instrument. quoted_at is the quote's
-- envelope eventTime; a quote that is not newer (a redelivery or a replay) is ignored.
CREATE TABLE IF NOT EXISTS watchlist_quotes (
    symbol      VARCHAR(20)   PRIMARY KEY,
    last_price  DECIMAL(19,4) NOT NULL CHECK (last_price > 0),
    quoted_at   TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
