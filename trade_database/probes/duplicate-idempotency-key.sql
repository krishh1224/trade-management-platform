-- 1. Insert the first valid order with an idempotency key
INSERT INTO orders (
    order_id, 
    portfolio_id, 
    instrument_id, 
    quantity, 
    status, 
    side, 
    type, 
    idempotency_key
) VALUES (
    9998, 
    3001, 
    4001, 
    10.000000, 
    'OPEN', 
    'BUY', 
    'MARKET', 
    'idem-key-123'
);

-- 2. Attempt to insert a different order but with the exact same idempotency key
-- The harness expects this to FAIL with SQLSTATE 23505 (Unique Violation)
INSERT INTO orders (
    order_id, 
    portfolio_id, 
    instrument_id, 
    quantity, 
    status, 
    side, 
    type, 
    idempotency_key
) VALUES (
    9999, 
    3001, 
    4001, 
    15.000000, 
    'OPEN', 
    'BUY', 
    'MARKET', 
    'idem-key-123'
);
