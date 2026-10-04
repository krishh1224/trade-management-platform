CREATE TABLE IF NOT EXISTS market_quote_snapshots (
    symbol VARCHAR(20) PRIMARY KEY,
    last_price DECIMAL(19,4) NOT NULL,
    change_pct DECIMAL(10,4),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_market_quote_snapshots_instrument
        FOREIGN KEY (symbol)
            REFERENCES instruments(ticker)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_market_quote_snapshots_updated_at
    ON market_quote_snapshots(updated_at DESC);
