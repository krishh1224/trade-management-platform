-- Multi-exchange instruments and currency conversion at settlement.
--
-- 1. instruments.exchange: V1 only allowed NSE and BSE, so US equities, FX pairs
--    and crypto could not be stored. Fauxnance carries no exchange metadata, so
--    the value is assigned per instrument in the seed:
--      NSE / BSE  - Indian equities (.NS / .BO suffix)
--      NASDAQ     - US equities listed on Nasdaq (plain ticker)
--      NYSE       - US equities listed on the NYSE (plain ticker)
--      FOREX      - currency pairs (FX: prefix)
--      CRYPTO     - crypto pairs (X: prefix)
--
-- 2. orders.fx_rate / settlement_currency / settlement_amount: the executor
--    settles every order in the account's base currency. When the instrument
--    trades in a different currency (e.g. AAPL in USD against an INR account)
--    it converts at the live Fauxnance FX rate and records here what it used,
--    so the cash movement on accounts.balance can always be reproduced:
--      settlement_amount = executed_price * quantity * fx_rate  (rounded to 2dp)
--    fx_rate is 1 when no conversion was needed. All three stay NULL for
--    orders that never filled and for rows that predate this migration.

ALTER TABLE instruments
    DROP CONSTRAINT IF EXISTS instruments_exchange_check;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_instruments_exchange'
    ) THEN
        ALTER TABLE instruments
            ADD CONSTRAINT chk_instruments_exchange
                CHECK (exchange IN ('NSE', 'BSE', 'NASDAQ', 'NYSE', 'FOREX', 'CRYPTO'));
    END IF;
END $$;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS fx_rate DECIMAL(19,8);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS settlement_amount DECIMAL(19,4);
