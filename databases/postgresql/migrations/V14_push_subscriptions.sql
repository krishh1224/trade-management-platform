-- Browser (Web Push) subscriptions for the notifications PUSH channel.
-- One row per browser that a customer enabled push on. A browser belongs to one account at a time:
-- enabling push for another account on the same browser moves the row (endpoint is unique).

CREATE TABLE IF NOT EXISTS push_subscriptions (
    subscription_id BIGSERIAL PRIMARY KEY,
    account_id      BIGINT        NOT NULL,
    endpoint        VARCHAR(1000) NOT NULL,   -- push-service URL; only allow-listed push hosts are stored
    p256dh          VARCHAR(200)  NOT NULL,   -- browser's public key, encrypts the payload
    auth            VARCHAR(100)  NOT NULL,   -- browser's auth secret, encrypts the payload
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint),

    CONSTRAINT fk_push_subscriptions_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_push_subscriptions_account
    ON push_subscriptions(account_id);
