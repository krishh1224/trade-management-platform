-- Sprint 8: Refresh token store for the nest-auth service.
--
-- Refresh tokens are:
-- - Stored as hashes (never raw tokens), so database read access is not session takeover
-- - Rotated on every refresh (consumed_at is set, new token issued)
-- - Revoked on reuse (entire family/chain revoked if a consumed token is presented again)
-- - Tracked by account_id and user (sub claim, UUID)
-- - Short-lived (7 days, expires_at)
--
-- family_id links all tokens issued from the same initial login or refresh, allowing
-- revocation of the entire chain if a consumed token is replayed (theft detection).
--
-- roles are stored alongside the token to ensure that when the token is consumed,
-- we can issue a new access token with the same (server-verified) identity, not
-- relying on any client-supplied data. This prevents privilege escalation if roles
-- are changed - the client must re-authenticate to get a new refresh token with
-- updated roles.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES accounts(acc_id),
    user_id          UUID NOT NULL,
    roles            TEXT[] NOT NULL,
    token_hash       VARCHAR(255) NOT NULL,
    family_id        UUID NOT NULL,
    expires_at       TIMESTAMP NOT NULL,
    consumed_at      TIMESTAMP,
    revoked_at       TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indices for common lookups:
-- - Finding a token by hash (for consumption/validation)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);
-- - Finding live tokens for an account (for revocation on theft)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_account_live ON refresh_tokens(account_id, revoked_at) WHERE revoked_at IS NULL;
-- - Finding expired tokens (for cleanup/auditing)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);
