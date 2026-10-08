-- Automated strategy execution, owned by the Strategies module in the Trade REST API
-- (com.team5.strategies). See src/main/java/com/team5/strategies/README.md in order-service.
--
-- A customer configures a rules-based strategy once; the module evaluates it against every market-data
-- quote for its symbol and, when the rule fires, places an order through POST /api/v1/orders. These are
-- the only tables the module writes. It reads accounts, instruments, portfolio, portfolio_holdings and
-- orders, and never writes any of them: orders are only ever created by the Trade API's own route.
--
-- strategies: one row per configured strategy, and the lock every order decision is taken under.
--
--   ENABLED   -> evaluated against every quote for its symbol, may place orders
--   DISABLED  -> the customer switched it off; nothing is evaluated or placed
--   HALTED    -> the module switched it off after max_consecutive_failures failed orders in a row
--
-- spent is the running total of BUY orders this strategy has placed, at their limit price. It can
-- never exceed max_spend: the module checks before placing, and the CHECK below refuses the update
-- if a defect ever let it through.

CREATE TABLE IF NOT EXISTS strategies (
    strategy_id               BIGSERIAL     PRIMARY KEY,
    account_id                BIGINT        NOT NULL,
    name                      VARCHAR(60)   NOT NULL,
    symbol                    VARCHAR(20)   NOT NULL,
    strategy_type             VARCHAR(20)   NOT NULL,

    -- MA_CROSSOVER: the two simple moving averages, in quotes
    short_window              INT,
    long_window               INT,
    -- BOLLINGER_BANDS: the moving average the bands sit around, and their width in standard deviations
    band_window               INT,
    band_width                DECIMAL(6,3),

    quantity                  INT           NOT NULL,
    max_spend                 DECIMAL(19,2) NOT NULL,
    max_position              BIGINT        NOT NULL,
    max_consecutive_failures  INT           NOT NULL DEFAULT 3,

    status                    VARCHAR(10)   NOT NULL DEFAULT 'ENABLED',
    status_reason             VARCHAR(200),
    spent                     DECIMAL(19,2) NOT NULL DEFAULT 0,
    orders_placed             INT           NOT NULL DEFAULT 0,
    consecutive_failures      INT           NOT NULL DEFAULT 0,
    last_triggered_at         TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_strategies_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE,

    CONSTRAINT ck_strategies_type
        CHECK (strategy_type IN ('MA_CROSSOVER', 'BOLLINGER_BANDS')),

    CONSTRAINT ck_strategies_parameters
        CHECK (
            (strategy_type = 'MA_CROSSOVER'
                AND short_window >= 2 AND long_window <= 200 AND short_window < long_window
                AND band_window IS NULL AND band_width IS NULL)
            OR
            (strategy_type = 'BOLLINGER_BANDS'
                AND band_window BETWEEN 2 AND 200 AND band_width > 0 AND band_width <= 5
                AND short_window IS NULL AND long_window IS NULL)
        ),

    CONSTRAINT ck_strategies_status
        CHECK (status IN ('ENABLED', 'DISABLED', 'HALTED')),

    CONSTRAINT ck_strategies_limits
        CHECK (quantity > 0 AND max_spend > 0 AND max_position > 0
            AND max_consecutive_failures BETWEEN 1 AND 10),

    -- the spend cap, enforced by the database as well as by the code that decides
    CONSTRAINT ck_strategies_spend_cap
        CHECK (spent >= 0 AND spent <= max_spend),

    CONSTRAINT ck_strategies_counters
        CHECK (orders_placed >= 0 AND consecutive_failures >= 0)
);

-- Every quote loads the enabled strategies for its symbol; disabled and halted ones stay out of it.
CREATE INDEX IF NOT EXISTS idx_strategies_enabled_symbol
    ON strategies (symbol)
    WHERE status = 'ENABLED';

CREATE INDEX IF NOT EXISTS idx_strategies_account
    ON strategies (account_id, created_at DESC);

-- strategy_executions: what the module did each time a strategy's rule fired. One row per strategy and
-- quote, so a redelivered or replayed quote can never act twice for the same strategy.
CREATE TABLE IF NOT EXISTS strategy_executions (
    execution_id      BIGSERIAL     PRIMARY KEY,
    strategy_id       BIGINT        NOT NULL,
    quote_event_id    UUID          NOT NULL,
    signal            VARCHAR(4)    NOT NULL,
    outcome           VARCHAR(25)   NOT NULL,
    quote_price       DECIMAL(19,4) NOT NULL,
    order_price       DECIMAL(19,2),
    quantity          INT,
    idempotency_key   VARCHAR(100),
    order_id          VARCHAR(60),
    detail            VARCHAR(300),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_strategy_executions_quote UNIQUE (strategy_id, quote_event_id),

    CONSTRAINT fk_strategy_executions_strategy
        FOREIGN KEY (strategy_id)
            REFERENCES strategies(strategy_id)
            ON DELETE CASCADE,

    CONSTRAINT ck_strategy_executions_signal
        CHECK (signal IN ('BUY', 'SELL')),

    CONSTRAINT ck_strategy_executions_outcome
        CHECK (outcome IN ('PLACED', 'FAILED', 'SKIPPED_DISABLED', 'SKIPPED_SPEND_LIMIT',
                           'SKIPPED_POSITION_LIMIT', 'SKIPPED_NO_HOLDING'))
);

CREATE INDEX IF NOT EXISTS idx_strategy_executions_strategy_created
    ON strategy_executions (strategy_id, created_at DESC);

-- strategy_quotes: the recent prices per symbol the indicators are computed from, as consumed from
-- market-data. quoted_at is the quote's envelope eventTime; one quote per symbol and instant. Only
-- the most recent few hundred per symbol are kept.
CREATE TABLE IF NOT EXISTS strategy_quotes (
    symbol      VARCHAR(20)   NOT NULL,
    quoted_at   TIMESTAMPTZ   NOT NULL,
    price       DECIMAL(19,4) NOT NULL CHECK (price > 0),
    event_id    UUID          NOT NULL,

    PRIMARY KEY (symbol, quoted_at)
);
