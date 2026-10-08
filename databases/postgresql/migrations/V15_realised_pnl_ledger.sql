-- Realised profit and loss, booked once per sale.
--
-- Owned by the Portfolio module in the Trade REST API. It is the only table that module writes; every
-- trading table (orders, portfolio_holdings, accounts) is read-only to it.
--
-- One row per SELL order, keyed by order_id. A row is inserted the first time the sale is seen and
-- never updated afterwards, so realised P&L is accumulated from sales and is independent of today's
-- price. The key makes booking idempotent: seeing the same sale twice (a replayed event, a second
-- reconciliation, two requests racing) inserts nothing the second time.
--
--   realised_pnl      = (fill_price - average_cost_at_sale) * quantity_sold    in the instrument's currency
--   realised_pnl_base = realised_pnl * fx_rate                                  in the account's base currency
--
-- fx_rate is the rate the executor applied when the order filled (orders.fx_rate), 1 when the
-- instrument is quoted in the base currency.

CREATE TABLE IF NOT EXISTS realised_pnl_ledger (
    order_id             BIGINT        PRIMARY KEY,
    account_id           BIGINT        NOT NULL,
    symbol               VARCHAR(20)   NOT NULL,
    instrument_currency  VARCHAR(3)    NOT NULL,
    base_currency        VARCHAR(3)    NOT NULL,
    quantity_sold        BIGINT        NOT NULL CHECK (quantity_sold > 0),
    fill_price           DECIMAL(19,4) NOT NULL CHECK (fill_price > 0),
    average_cost_at_sale DECIMAL(19,4) NOT NULL CHECK (average_cost_at_sale >= 0),
    fx_rate              DECIMAL(19,8) NOT NULL CHECK (fx_rate > 0),
    realised_pnl         DECIMAL(19,4) NOT NULL,
    realised_pnl_base    DECIMAL(19,2) NOT NULL,
    sold_at              TIMESTAMP     NOT NULL,
    booked_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_realised_pnl_ledger_account_sold
    ON realised_pnl_ledger (account_id, sold_at);
