-- Attempt to insert an order referencing a portfolio_id (999999) that does not exist.
-- The harness expects this to FAIL with SQLSTATE 23503 (Foreign Key Violation)
INSERT INTO orders (
    order_id, 
    portfolio_id, 
    instrument_id, 
    quantity, 
    status, 
    side, 
    type
) VALUES (
    8888, 
    999999, 
    4001, 
    10.000000, 
    'OPEN', 
    'BUY', 
    'MARKET'
);
