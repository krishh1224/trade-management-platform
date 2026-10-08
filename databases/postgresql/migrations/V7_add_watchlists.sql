CREATE TABLE IF NOT EXISTS watchlists (
    watchlist_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlists_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_watchlists_instrument
        FOREIGN KEY (instrument_id)
            REFERENCES instruments(instrument_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_watchlists_account_instrument
        UNIQUE (account_id, instrument_id)
);

CREATE INDEX IF NOT EXISTS idx_watchlists_account_id
    ON watchlists(account_id);

CREATE INDEX IF NOT EXISTS idx_watchlists_added_at
    ON watchlists(added_at DESC);
