DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type
        WHERE typname = 'alert_channel_type'
    ) THEN
        CREATE TYPE alert_channel_type AS ENUM ('EMAIL', 'PUSH', 'BROWSER', 'SMS', 'NONE');
    END IF;
END
$$;

ALTER TABLE customer_preferences
    ALTER COLUMN alert_channel DROP DEFAULT;

ALTER TABLE customer_preferences
    ALTER COLUMN alert_channel TYPE alert_channel_type
    USING (
        CASE UPPER(TRIM(alert_channel))
            WHEN 'EMAIL' THEN 'EMAIL'::alert_channel_type
            WHEN 'PUSH' THEN 'PUSH'::alert_channel_type
            WHEN 'BROWSER' THEN 'BROWSER'::alert_channel_type
            WHEN 'SMS' THEN 'SMS'::alert_channel_type
            WHEN 'NONE' THEN 'NONE'::alert_channel_type
            ELSE 'NONE'::alert_channel_type
        END
    );

ALTER TABLE customer_preferences
    ALTER COLUMN alert_channel SET DEFAULT 'NONE'::alert_channel_type;

ALTER TABLE customer_preferences
    ALTER COLUMN alert_channel SET NOT NULL;
