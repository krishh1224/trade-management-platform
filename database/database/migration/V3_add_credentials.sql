-- Sprint 8: Credential store for the nest-auth service.
--
-- account_id (not client_id) is the FK on purpose: trade-api's JWT contract
-- and its authorization checks (AccountServiceImpl.assertAccountIsReadable)
-- are scoped to a single accountId claim, not a client. Tying the credential
-- directly to the account it logs into means a successful login resolves
-- straight to the JWT claim, with no "which of this client's accounts"
-- lookup in between. The schema's accounts.client_id is NOT unique (a client
-- can own multiple accounts), so if that ever needs one login across several
-- accounts, this table will need to change - not assumed away here.
--
-- No name/display column here, by design: a client's name already lives in
-- clients.name, reachable via accounts.client_id. Duplicating it onto this
-- row would be exactly the kind of denormalization the rest of this schema
-- avoids (accounts doesn't copy clients.name, portfolio doesn't copy
-- anything from accounts, etc). If a display name is ever needed alongside
-- a credential, join through accounts -> clients for it.

CREATE TABLE IF NOT EXISTS credentials (
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT NOT NULL UNIQUE REFERENCES accounts(acc_id),
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- password_hash always holds a bcrypt digest ($2a$/$2b$/$2y$ prefix, cost >= 12).
-- Plaintext passwords are never written to this column or anywhere else.
