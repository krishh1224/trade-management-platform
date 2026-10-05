INSERT INTO accounts
(acc_id, account_number, risk_profile, base_currency, balance, status)
VALUES
(101, '1000000001', 'LOW', 'INR', 98000.0000, 'ACTIVE'),
(102, '1000000002', 'MODERATE', 'INR', 156000.0000, 'ACTIVE'),
(103, '1000000003', 'ADVENTUROUS', 'INR', 82000.0000, 'ACTIVE'),
(104, '1000000004', 'LOW', 'INR', 116600.0000, 'ACTIVE'),
(105, '1000000005', 'MODERATE', 'INR', 95000.0000, 'SUSPENDED'),
(106, '1000000006', 'ADVENTUROUS', 'INR', 65000.0000, 'ACTIVE');

INSERT INTO clients (id, acc_id, name, email, phone, dob) VALUES
(1, 101, 'Sreyansh Baid', 'sreyansh.baid@example.com', '9876500001', '1995-04-12'),
(2, 102, 'Riya Rangwani', 'riya.rangwani@example.com', '9876500002', '1992-08-25'),
(3, 103, 'Krishna Gupta', 'krishna.gupta@example.com', '9876500003', '1988-01-17'),
(4, 104, 'Ananya Singh', 'ananya.singh@example.com', '9876500004', '1997-11-03'),
(5, 105, 'Sameer Patil', 'sameer.patil@example.com', '9876500005', '1990-06-21'),
(6, 106, 'Meera Iyer', 'meera.iyer@example.com', '9876500006', '1994-09-14');

INSERT INTO portfolio
(portfolio_id, name, account_id, status)
VALUES
(201, 'Sreyansh Baid Portfolio', 101, 'ACTIVE'),
(202, 'Riya Rangwani Portfolio', 102, 'ACTIVE'),
(203, 'Krishna Gupta Portfolio', 103, 'ACTIVE'),
(204, 'Ananya Singh Portfolio', 104, 'ACTIVE'),
(205, 'Sameer Patil Portfolio', 105, 'ACTIVE'),
(206, 'Meera Iyer Portfolio', 106, 'ACTIVE');

INSERT INTO instruments
(instrument_id, name, ticker, asset_class, currency, exchange, tradable)
VALUES

-- =========================================================
-- CORE INDIAN EQUITIES - 6 (referenced by the holdings, orders
-- and transactions below)
-- =========================================================

(301, 'Infosys Limited', 'INFY.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(302, 'Reliance Industries Limited', 'RELIANCE.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(303, 'Tata Steel Limited', 'TATASTEEL.BO', 'EQUITY', 'INR', 'BSE', TRUE),
(304, 'HDFC Bank Limited', 'HDFCBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(305, 'ICICI Bank Limited', 'ICICIBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(306, 'ITC Limited', 'ITC.NS', 'EQUITY', 'INR', 'NSE', TRUE),

-- =========================================================
-- US EQUITIES - 20 (15 NASDAQ, 5 NYSE)
-- =========================================================

(307, 'Apple Inc.', 'AAPL', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(308, 'Microsoft Corporation', 'MSFT', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(309, 'Amazon.com Inc.', 'AMZN', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(310, 'Google (Alphabet Inc.) Class A', 'GOOGL', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(311, 'Google (Alphabet Inc.) Class C', 'GOOG', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(312, 'NVIDIA Corporation', 'NVDA', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(313, 'Meta Platforms Inc.', 'META', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(314, 'Tesla Inc.', 'TSLA', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(315, 'Netflix Inc.', 'NFLX', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(316, 'Adobe Inc.', 'ADBE', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(317, 'Intel Corporation', 'INTC', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(318, 'Advanced Micro Devices Inc.', 'AMD', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(319, 'PayPal Holdings Inc.', 'PYPL', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(320, 'Cisco Systems Inc.', 'CSCO', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(321, 'Broadcom Inc.', 'AVGO', 'EQUITY', 'USD', 'NASDAQ', TRUE),
(322, 'JPMorgan Chase & Co.', 'JPM', 'EQUITY', 'USD', 'NYSE', TRUE),
(323, 'Visa Inc.', 'V', 'EQUITY', 'USD', 'NYSE', TRUE),
(324, 'Mastercard Incorporated', 'MA', 'EQUITY', 'USD', 'NYSE', TRUE),
(325, 'The Coca-Cola Company', 'KO', 'EQUITY', 'USD', 'NYSE', TRUE),
(326, 'Walmart Inc.', 'WMT', 'EQUITY', 'USD', 'NYSE', TRUE),

-- =========================================================
-- INDIAN EQUITIES - 15
-- =========================================================

(327, 'Tata Consultancy Services Limited', 'TCS.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(328, 'State Bank of India', 'SBIN.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(329, 'Bharti Airtel Limited', 'BHARTIARTL.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(330, 'Larsen & Toubro Limited', 'LT.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(331, 'Tata Motors Limited', 'TATAMOTORS.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(332, 'Hindustan Unilever Limited', 'HINDUNILVR.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(333, 'Maruti Suzuki India Limited', 'MARUTI.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(334, 'Asian Paints Limited', 'ASIANPAINT.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(335, 'Adani Enterprises Limited', 'ADANIENT.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(336, 'Axis Bank Limited', 'AXISBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(337, 'Kotak Mahindra Bank Limited', 'KOTAKBANK.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(338, 'Sun Pharmaceutical Industries Limited', 'SUNPHARMA.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(339, 'Wipro Limited', 'WIPRO.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(340, 'HCL Technologies Limited', 'HCLTECH.NS', 'EQUITY', 'INR', 'NSE', TRUE),
(341, 'Bajaj Finance Limited', 'BAJFINANCE.NS', 'EQUITY', 'INR', 'NSE', TRUE),

-- =========================================================
-- FOREIGN EXCHANGE - 8
-- =========================================================

(342, 'Euro / US Dollar', 'FX:EURUSD', 'FX', 'USD', 'FOREX', TRUE),
(343, 'British Pound / US Dollar', 'FX:GBPUSD', 'FX', 'USD', 'FOREX', TRUE),
(344, 'US Dollar / Japanese Yen', 'FX:USDJPY', 'FX', 'JPY', 'FOREX', TRUE),
(345, 'US Dollar / Swiss Franc', 'FX:USDCHF', 'FX', 'CHF', 'FOREX', TRUE),
(346, 'Australian Dollar / US Dollar', 'FX:AUDUSD', 'FX', 'USD', 'FOREX', TRUE),
(347, 'US Dollar / Canadian Dollar', 'FX:USDCAD', 'FX', 'CAD', 'FOREX', TRUE),
(348, 'US Dollar / Indian Rupee', 'FX:USDINR', 'FX', 'INR', 'FOREX', TRUE),
(349, 'Euro / British Pound', 'FX:EURGBP', 'FX', 'GBP', 'FOREX', TRUE),

-- =========================================================
-- CRYPTO - 7
-- =========================================================

(350, 'Bitcoin', 'X:BTC-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(351, 'Ethereum', 'X:ETH-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(352, 'Tether', 'X:USDT-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(353, 'BNB', 'X:BNB-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(354, 'XRP', 'X:XRP-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(355, 'Solana', 'X:SOL-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE),
(356, 'Cardano', 'X:ADA-USD', 'CRYPTO', 'USD', 'CRYPTO', TRUE);





