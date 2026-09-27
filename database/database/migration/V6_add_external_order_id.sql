-- Sprint 8 follow-up: persist the UUID-based external order id.
--
-- placeOrder/cancelOrder generate a random UUID, derive a lossy BIGINT
-- (toPositiveLong: XOR of the UUID's two halves) to use as the orders.order_id
-- primary key, and return "ORD-<uuid>" in their response. That derivation is
-- one-way: the original UUID cannot be recovered from order_id alone. Without
-- this column, any endpoint reading orders back from the database (such as
-- GET /api/v1/accounts/{id}/orders) can only ever show the raw numeric
-- order_id, not the "ORD-<uuid>" format the contract requires.
--
-- Nullable on purpose: the Sprint 3 seed rows (order_id 501-515) predate the
-- UUID scheme and have no UUID to store.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS external_order_id VARCHAR(50);
