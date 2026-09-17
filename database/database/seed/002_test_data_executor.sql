-- Test data for Executor Kafka consumer testing
-- Account 101 (already exists, portfolio_id=201)
-- Account 100 (new test account, portfolio_id=1000)

-- Insert test client for account 100
INSERT INTO clients (id, name, email, phone, dob) 
VALUES (100, 'Test Client Account 100', 'test100@example.com', '9999999999', '2000-01-01')
ON CONFLICT DO NOTHING;

-- Insert test account 100 (new)
INSERT INTO accounts (acc_id, client_id, account_number, risk_profile, base_currency, balance, status)
VALUES (100, 100, '1000000100', 'MODERATE', 'INR', 500000.0000, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Insert test portfolio for account 100
INSERT INTO portfolio (portfolio_id, name, account_id, status)
VALUES (1000, 'Test Executor Portfolio (Account 100)', 100, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Insert test orders for account 100 (portfolio_id=1000)
-- accountId=100 → UUID="dee1b92e-b08f-3a3f-a58c-45ca7996fc52" → orderIdLong=8894042898683250285
INSERT INTO orders (order_id, portfolio_id, instrument_id, quantity, price, status, side, idempotency_key)
VALUES 
  (8894042898683250285, 1000, 301, 10, 500.000000, 'PENDING', 'BUY', 'key-1789656275935'),
  (8894042898683250286, 1000, 301, 10, 500.000000, 'PENDING', 'BUY', 'key-1789656727907'),
  (8894042898683250287, 1000, 301, 10, 500.000000, 'PENDING', 'BUY', 'key-1789656728123'),
  (8894042898683250288, 1000, 301, 10, 500.000000, 'PENDING', 'BUY', 'key-1789656728456')
ON CONFLICT DO NOTHING;

-- Insert test orders for account 101 (portfolio_id=201, which already exists)
-- UUID="4efb554f-3022-4263-86df-9712928d3dd6" → orderIdLong=4024878459545223243 (from Kafka message)
INSERT INTO orders (order_id, portfolio_id, instrument_id, quantity, price, status, side, idempotency_key)
VALUES 
  (4024878459545223243, 201, 301, 10, 8.000000, 'PENDING', 'BUY', 'abcdefghijklmnopfgf'),
  (4024878459545223244, 201, 301, 15, 1500.000000, 'PENDING', 'BUY', 'test-order-2'),
  (4024878459545223245, 201, 301, 20, 2850.000000, 'PENDING', 'BUY', 'test-order-3')
ON CONFLICT DO NOTHING;

-- Insert corresponding transactions for account 100
INSERT INTO transactions (transaction_id, account_id, order_id, instrument_id, transaction_type, quantity, price, amount, currency, balance_before, balance_after, idempotency_key)
VALUES
  (10001, 100, 8894042898683250285, 301, 'BUY', 10, 500.000000, 5000.0000, 'INR', 500000.0000, 495000.0000, 'txn-key-1789656275935'),
  (10002, 100, 8894042898683250286, 301, 'BUY', 10, 500.000000, 5000.0000, 'INR', 495000.0000, 490000.0000, 'txn-key-1789656727907'),
  (10003, 100, 8894042898683250287, 301, 'BUY', 10, 500.000000, 5000.0000, 'INR', 490000.0000, 485000.0000, 'txn-key-1789656728123'),
  (10004, 100, 8894042898683250288, 301, 'BUY', 10, 500.000000, 5000.0000, 'INR', 485000.0000, 480000.0000, 'txn-key-1789656728456')
ON CONFLICT DO NOTHING;

-- Insert corresponding transactions for account 101
INSERT INTO transactions (transaction_id, account_id, order_id, instrument_id, transaction_type, quantity, price, amount, currency, balance_before, balance_after, idempotency_key)
VALUES
  (10005, 101, 4024878459545223243, 301, 'BUY', 10, 8.000000, 80.0000, 'INR', 98000.0000, 97920.0000, 'abcdefghijklmnopfgf'),
  (10006, 101, 4024878459545223244, 301, 'BUY', 15, 1500.000000, 22500.0000, 'INR', 97920.0000, 75420.0000, 'test-order-2'),
  (10007, 101, 4024878459545223245, 301, 'BUY', 20, 2850.000000, 57000.0000, 'INR', 75420.0000, 18420.0000, 'test-order-3')
ON CONFLICT DO NOTHING;

-- Insert portfolio holdings for account 100
INSERT INTO portfolio_holdings (portfolio_holding_id, portfolio_id, instrument_id, quantity, avg_cost)
VALUES (501, 1000, 301, 40, 500.000000)
ON CONFLICT DO NOTHING;
