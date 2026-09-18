-- Sprint 6: Add settlement tracking columns to orders table
-- These columns capture the execution result and are updated atomically during settlement

ALTER TABLE orders
ADD COLUMN executed_price DECIMAL(19,4),
ADD COLUMN executed_on TIMESTAMP,
ADD COLUMN rejection_reason VARCHAR(255);

-- Ensure these columns are nullable to handle unfilled/rejected orders
-- executed_price: populated only on FILLED status
-- executed_on: populated only on terminal status (FILLED or REJECTED)
-- rejection_reason: populated only on REJECTED status
