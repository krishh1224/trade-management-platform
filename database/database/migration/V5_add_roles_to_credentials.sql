-- Sprint 8 follow-up: roles for credentials.
--
-- Keeps compatibility for existing rows by defaulting to USER.

ALTER TABLE credentials
    ADD COLUMN IF NOT EXISTS roles VARCHAR(16) NOT NULL DEFAULT 'USER';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_credentials_roles'
    ) THEN
        ALTER TABLE credentials
            ADD CONSTRAINT chk_credentials_roles
                CHECK (roles IN ('USER', 'ADMIN'));
    END IF;
END $$;
