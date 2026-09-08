CREATE OR REPLACE FUNCTION generate_account_number()
RETURNS VARCHAR(10)
LANGUAGE plpgsql
AS $$
DECLARE
    new_account_number VARCHAR(10);
BEGIN
    LOOP
        new_account_number :=
            LPAD(
                FLOOR(RANDOM() * 10000000000)::BIGINT::TEXT,
                10,
                '0'
            );

        EXIT WHEN NOT EXISTS (
            SELECT 1
            FROM accounts
            WHERE account_number = new_account_number
        );
    END LOOP;

    RETURN new_account_number;
END;
$$;


CREATE TABLE clients (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20) NOT NULL UNIQUE,
    dob             DATE NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE accounts (
    acc_id              BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL,
    account_number      VARCHAR(10) NOT NULL UNIQUE
                        DEFAULT generate_account_number(),
    risk_profile        VARCHAR(20) NOT NULL
                        CHECK (risk_profile IN ('LOW', 'MODERATE', 'ADVENTUROUS')),
    base_currency       VARCHAR(3) NOT NULL,
    balance             DECIMAL(19,2) NOT NULL DEFAULT 0
                        CHECK (balance >= 0),
    status              VARCHAR(20) NOT NULL
                        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_client
        FOREIGN KEY (client_id)
        REFERENCES clients(id)
);


CREATE TABLE portfolio (
    portfolio_id        BIGINT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    account_id          BIGINT NOT NULL UNIQUE,
    status              VARCHAR(30) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_portfolio_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(acc_id)
);


CREATE TABLE instruments (
    instrument_id       BIGINT PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    ticker              VARCHAR(20) NOT NULL UNIQUE,
    asset_class         VARCHAR(50) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    exchange            VARCHAR(10) NOT NULL
                        CHECK (exchange IN ('NSE', 'BSE')),
    tradable            BOOLEAN NOT NULL DEFAULT TRUE
);


CREATE TABLE portfolio_holdings (
    portfolio_holding_id BIGINT PRIMARY KEY,
    portfolio_id         BIGINT NOT NULL,
    instrument_id        BIGINT NOT NULL,
    quantity             BIGINT NOT NULL
                        CHECK (quantity > 0),
    avg_cost             DECIMAL(19,4) NOT NULL
                        CHECK (avg_cost >= 0),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_holdings_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id),

    CONSTRAINT fk_holdings_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT uq_holdings_portfolio_instrument
        UNIQUE (portfolio_id, instrument_id)
);


CREATE TABLE orders (
    order_id            BIGINT PRIMARY KEY,
    portfolio_id        BIGINT NOT NULL,
    instrument_id       BIGINT NOT NULL,
    quantity            BIGINT NOT NULL
                        CHECK (quantity > 0),
    price               DECIMAL(19,4) NOT NULL
                        CHECK (price > 0),
    status              VARCHAR(30) NOT NULL,
    side                VARCHAR(10) NOT NULL
                        CHECK (side IN ('BUY', 'SELL')),
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP,

    CONSTRAINT fk_orders_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id),

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id)
);


CREATE TABLE transactions (
    transaction_id      BIGINT PRIMARY KEY,
    account_id          BIGINT NOT NULL,
    order_id            BIGINT,
    instrument_id       BIGINT,

    transaction_type    VARCHAR(20) NOT NULL
                        CHECK (
                            transaction_type IN (
                                'BUY',
                                'SELL',
                                'DEPOSIT',
                                'WITHDRAWAL'
                            )
                        ),

    quantity            BIGINT,
    price               DECIMAL(19,4),

    amount              DECIMAL(19,4) NOT NULL
                        CHECK (amount > 0),

    currency            VARCHAR(3) NOT NULL,

    balance_before      DECIMAL(19,4) NOT NULL
                        CHECK (balance_before >= 0),

    balance_after       DECIMAL(19,4) NOT NULL
                        CHECK (balance_after >= 0),

    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(acc_id),

    CONSTRAINT fk_transactions_order
        FOREIGN KEY (order_id)
        REFERENCES orders(order_id),

    CONSTRAINT fk_transactions_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instruments(instrument_id),

    CONSTRAINT chk_transaction_quantity
        CHECK (
            transaction_type IN ('DEPOSIT', 'WITHDRAWAL')
            OR (quantity IS NOT NULL AND quantity > 0)
        ),

    CONSTRAINT chk_transaction_price
        CHECK (
            transaction_type IN ('DEPOSIT', 'WITHDRAWAL')
            OR (price IS NOT NULL AND price > 0)
        ),

    CONSTRAINT chk_transaction_order
        CHECK (
            transaction_type IN ('DEPOSIT', 'WITHDRAWAL')
            OR order_id IS NOT NULL
        ),

    CONSTRAINT chk_transaction_instrument
        CHECK (
            transaction_type IN ('DEPOSIT', 'WITHDRAWAL')
            OR instrument_id IS NOT NULL
        ),

    CONSTRAINT chk_cash_transaction_order
        CHECK (
            transaction_type IN ('BUY', 'SELL')
            OR order_id IS NULL
        ),

    CONSTRAINT chk_cash_transaction_instrument
        CHECK (
            transaction_type IN ('BUY', 'SELL')
            OR instrument_id IS NULL
        )
);


CREATE INDEX idx_accounts_client_id
    ON accounts(client_id);

CREATE INDEX idx_portfolio_holdings_portfolio_id
    ON portfolio_holdings(portfolio_id);

CREATE INDEX idx_portfolio_holdings_instrument_id
    ON portfolio_holdings(instrument_id);

CREATE INDEX idx_orders_portfolio_id
    ON orders(portfolio_id);

CREATE INDEX idx_orders_instrument_id
    ON orders(instrument_id);

CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

CREATE INDEX idx_transactions_order_id
    ON transactions(order_id);

CREATE INDEX idx_transactions_instrument_id
    ON transactions(instrument_id);

CREATE INDEX idx_transactions_created_at
    ON transactions(created_at);