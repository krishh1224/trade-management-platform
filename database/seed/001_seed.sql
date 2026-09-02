INSERT INTO clients (id, name, email, phone, dob) VALUES
(1, 'Aarav Sharma', 'aarav.sharma@example.com', '9876500001', '1995-04-12'),
(2, 'Diya Patel', 'diya.patel@example.com', '9876500002', '1992-08-25'),
(3, 'Rohan Mehta', 'rohan.mehta@example.com', '9876500003', '1988-01-17'),
(4, 'Ananya Singh', 'ananya.singh@example.com', '9876500004', '1997-11-03'),
(5, 'Kabir Shah', 'kabir.shah@example.com', '9876500005', '1990-06-21'),
(6, 'Meera Iyer', 'meera.iyer@example.com', '9876500006', '1994-09-14');

INSERT INTO accounts
(acc_id, client_id, account_number, risk_profile, base_currency, balance, status)
VALUES
(101, 1, '1000000001', 'LOW', 'INR', 98000.0000, 'ACTIVE'),
(102, 2, '1000000002', 'MODERATE', 'INR', 156000.0000, 'ACTIVE'),
(103, 3, '1000000003', 'ADVENTUROUS', 'INR', 82000.0000, 'ACTIVE'),
(104, 4, '1000000004', 'LOW', 'INR', 116600.0000, 'ACTIVE'),
(105, 5, '1000000005', 'MODERATE', 'INR', 95000.0000, 'SUSPENDED'),
(106, 6, '1000000006', 'ADVENTUROUS', 'INR', 65000.0000, 'ACTIVE');

INSERT INTO portfolio
(portfolio_id, name, account_id, status)
VALUES
(201, 'Aarav Equity Portfolio', 101, 'ACTIVE'),
(202, 'Diya Equity Portfolio', 102, 'ACTIVE'),
(203, 'Rohan Growth Portfolio', 103, 'ACTIVE'),
(204, 'Ananya Equity Portfolio', 104, 'ACTIVE'),
(205, 'Kabir Long Term Portfolio', 105, 'ACTIVE'),
(206, 'Meera Growth Portfolio', 106, 'ACTIVE');

INSERT INTO instruments
(instrument_id, name, ticker, asset_class, currency, exchange, tradable)
VALUES
(301, 'Infosys Limited', 'INFY.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(302, 'Reliance Industries Limited', 'RELIANCE.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(303, 'Tata Steel Limited', 'TATASTEEL.BO', 'EQUITY', 'INR', 'BSE', TRUE),
(304, 'HDFC Bank Limited', 'HDFCBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(305, 'ICICI Bank Limited', 'ICICIBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(306, 'ITC Limited', 'ITC.NS', 'EQUITY', 'INR', 'NSE', TRUE);

INSERT INTO portfolio_holdings
(portfolio_holding_id, portfolio_id, instrument_id, quantity, avg_cost)
VALUES
(401, 201, 301, 100.000000, 1450.000000),
(402, 201, 302, 50.000000, 2800.000000),
(403, 202, 303, 200.000000, 145.000000),
(404, 202, 304, 75.000000, 1650.000000),
(405, 203, 305, 100.000000, 950.000000),
(406, 203, 306, 150.000000, 430.000000),
(407, 204, 301, 60.000000, 1500.000000),
(408, 205, 302, 40.000000, 2750.000000),
(409, 206, 304, 80.000000, 1600.000000);

INSERT INTO orders
(order_id, portfolio_id, instrument_id, quantity, price, status, side, idempotency_key)
VALUES
(501, 201, 301, 20.000000, 1500.000000, 'FILLED', 'BUY', 'order-501'),
(502, 201, 302, 10.000000, 2850.000000, 'FILLED', 'BUY', 'order-502'),
(503, 201, 301, 5.000000, 1550.000000, 'FILLED', 'SELL', 'order-503'),
(504, 202, 303, 50.000000, 150.000000, 'FILLED', 'BUY', 'order-504'),
(505, 202, 304, 10.000000, 1700.000000, 'REJECTED', 'BUY', 'order-505'),
(506, 202, 304, 20.000000, 1725.000000, 'FILLED', 'SELL', 'order-506'),
(507, 203, 305, 30.000000, 980.000000, 'FILLED', 'BUY', 'order-507'),
(508, 203, 306, 25.000000, 440.000000, 'FILLED', 'BUY', 'order-508'),
(509, 203, 305, 10.000000, 1000.000000, 'CANCELLED', 'SELL', 'order-509'),
(510, 204, 301, 15.000000, 1520.000000, 'FILLED', 'BUY', 'order-510'),
(511, 204, 301, 5.000000, 1580.000000, 'NEW'    , 'SELL', 'order-511'),
(512, 205, 302, 10.000000, 2800.000000, 'FILLED', 'BUY', 'order-512'),
(513, 206, 304, 20.000000, 1650.000000, 'FILLED', 'BUY', 'order-513'),
(514, 206, 304, 10.000000, 1700.000000, 'FILLED', 'SELL', 'order-514'),
(515, 206, 306, 30.000000, 450.000000, 'FILLED', 'BUY', 'order-515');

INSERT INTO transactions
(transaction_id, account_id, order_id, instrument_id, transaction_type,
 quantity, price, amount, currency, balance_before, balance_after,
 idempotency_key)
VALUES
(601, 101, NULL, NULL, 'DEPOSIT',  NULL, NULL, 150000.0000, 'INR',      0.0000, 150000.0000, 'txn-601'),
(602, 101, 501, 301, 'BUY',      20.000000, 1500.000000, 30000.0000, 'INR', 150000.0000, 120000.0000, 'txn-602'),
(603, 101, 502, 302, 'BUY',      10.000000, 2850.000000, 28500.0000, 'INR', 120000.0000,  91500.0000, 'txn-603'),
(604, 101, 503, 301, 'SELL',      5.000000, 1550.000000,  7750.0000, 'INR',  91500.0000,  99250.0000, 'txn-604'),

(605, 102, NULL, NULL, 'DEPOSIT',  NULL, NULL, 200000.0000, 'INR',      0.0000, 200000.0000, 'txn-605'),
(606, 102, 504, 303, 'BUY',      50.000000,  150.000000,  7500.0000, 'INR', 200000.0000, 192500.0000, 'txn-606'),
(607, 102, 506, 304, 'SELL',     20.000000, 1725.000000, 34500.0000, 'INR', 192500.0000, 227000.0000, 'txn-607'),
(608, 102, NULL, NULL, 'WITHDRAWAL', NULL, NULL, 71000.0000, 'INR', 227000.0000, 156000.0000, 'txn-608'),

(609, 103, NULL, NULL, 'DEPOSIT',  NULL, NULL, 120000.0000, 'INR',      0.0000, 120000.0000, 'txn-609'),
(610, 103, 507, 305, 'BUY',      30.000000,  980.000000, 29400.0000, 'INR', 120000.0000,  90600.0000, 'txn-610'),
(611, 103, 508, 306, 'BUY',      25.000000,  440.000000, 11000.0000, 'INR',  90600.0000,  79600.0000, 'txn-611'),
(612, 103, NULL, NULL, 'DEPOSIT', NULL, NULL, 2400.0000, 'INR', 79600.0000, 82000.0000, 'txn-612'),

(613, 104, NULL, NULL, 'DEPOSIT',  NULL, NULL, 140000.0000, 'INR',      0.0000, 140000.0000, 'txn-613'),
(614, 104, 510, 301, 'BUY',      15.000000, 1520.000000, 22800.0000, 'INR', 140000.0000, 117200.0000, 'txn-614'),
(615, 104, NULL, NULL, 'WITHDRAWAL', NULL, NULL,  600.0000, 'INR', 117200.0000, 116600.0000, 'txn-615'),

(616, 105, NULL, NULL, 'DEPOSIT',  NULL, NULL, 100000.0000, 'INR',      0.0000, 100000.0000, 'txn-616'),
(617, 105, 512, 302, 'BUY',      10.000000, 2800.000000, 28000.0000, 'INR', 100000.0000,  72000.0000, 'txn-617'),
(618, 105, NULL, NULL, 'WITHDRAWAL', NULL, NULL, 23000.0000, 'INR',  72000.0000,  49000.0000, 'txn-618'),
(619, 105, NULL, NULL, 'DEPOSIT',  NULL, NULL, 46000.0000, 'INR',  49000.0000,  95000.0000, 'txn-619'),

(620, 106, NULL, NULL, 'DEPOSIT',  NULL, NULL, 100000.0000, 'INR',      0.0000, 100000.0000, 'txn-620'),
(621, 106, 513, 304, 'BUY',  20.000000, 1650.000000, 33000.0000, 'INR', 100000.0000,  67000.0000, 'txn-621'),
(622, 106, 514, 304, 'SELL',     10.000000, 1700.000000, 17000.0000, 'INR',  67000.0000,  84000.0000, 'txn-622'),
(623, 106, 515, 306, 'BUY',      30.000000,  450.000000, 13500.0000, 'INR',  84000.0000,  70500.0000, 'txn-623'),
(624, 106, NULL, NULL, 'WITHDRAWAL', NULL, NULL,  5500.0000, 'INR',  70500.0000,  65000.0000, 'txn-624');
