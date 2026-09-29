-- Sprint 6: Add settlement tracking columns to orders table
-- These columns capture the execution result and are updated atomically during settlement

DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'executed_price'
    ) THEN
        ALTER TABLE orders ADD COLUMN executed_price DECIMAL(19,4);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'executed_on'
    ) THEN
        ALTER TABLE orders ADD COLUMN executed_on TIMESTAMP;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'rejection_reason'
    ) THEN
        ALTER TABLE orders ADD COLUMN rejection_reason VARCHAR(255);
    END IF;
END $$;

-- Ensure these columns are nullable to handle unfilled/rejected orders
-- executed_price: populated only on FILLED status
-- executed_on: populated only on terminal status (FILLED or REJECTED)
-- rejection_reason: populated only on REJECTED status
