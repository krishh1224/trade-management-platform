CREATE TABLE IF NOT EXISTS watchlist_groups (
    watchlist_group_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlist_groups_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(acc_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_watchlist_groups_account_name
        UNIQUE (account_id, name)
);

ALTER TABLE watchlists
    ADD COLUMN IF NOT EXISTS watchlist_group_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'watchlists'
          AND column_name = 'watchlist_group_id'
    ) THEN
        INSERT INTO watchlist_groups (watchlist_group_id, account_id, name, created_at)
        SELECT DISTINCT
                        ABS(hashtext(account_id::text || ':favourite'))::BIGINT,
            account_id,
                        'Favourite',
            CURRENT_TIMESTAMP
        FROM watchlists w
        WHERE NOT EXISTS (
            SELECT 1
            FROM watchlist_groups wg
            WHERE wg.account_id = w.account_id
                            AND LOWER(wg.name) = 'favourite'
        );

        UPDATE watchlists w
        SET watchlist_group_id = wg.watchlist_group_id
        FROM watchlist_groups wg
        WHERE w.account_id = wg.account_id
                    AND LOWER(wg.name) = 'favourite'
          AND w.watchlist_group_id IS NULL;
    END IF;
END $$;

ALTER TABLE watchlists
    ALTER COLUMN watchlist_group_id SET NOT NULL;

ALTER TABLE watchlists
    DROP CONSTRAINT IF EXISTS uq_watchlists_account_instrument;

-- dropped first so the file can be re-run (apply.sh runs every migration on every deploy)
ALTER TABLE watchlists
    DROP CONSTRAINT IF EXISTS fk_watchlists_group;

ALTER TABLE watchlists
    ADD CONSTRAINT fk_watchlists_group
        FOREIGN KEY (watchlist_group_id)
            REFERENCES watchlist_groups(watchlist_group_id)
            ON DELETE CASCADE;

ALTER TABLE watchlists
    DROP CONSTRAINT IF EXISTS uq_watchlists_group_instrument;

ALTER TABLE watchlists
    ADD CONSTRAINT uq_watchlists_group_instrument
        UNIQUE (watchlist_group_id, instrument_id);

CREATE INDEX IF NOT EXISTS idx_watchlists_group_id
    ON watchlists(watchlist_group_id);
