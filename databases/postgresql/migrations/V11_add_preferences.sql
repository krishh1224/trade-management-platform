CREATE TABLE IF NOT EXISTS customer_preferences (
    acc_id BIGINT PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    theme VARCHAR(10) NOT NULL DEFAULT 'LIGHT',
    alert_channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL',

    CONSTRAINT fk_customer_preferences_account
        FOREIGN KEY (acc_id)
        REFERENCES accounts(acc_id)
);

INSERT INTO customer_preferences (
    acc_id,
    currency,
    theme,
    alert_channel
)
SELECT
    acc_id,
    base_currency,
    'LIGHT',
    'EMAIL'
FROM accounts
ON CONFLICT (acc_id) DO NOTHING;