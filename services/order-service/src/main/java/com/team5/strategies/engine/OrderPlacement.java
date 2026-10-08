package com.team5.strategies.engine;

// What the Trade API said to one POST /api/v1/orders.
public record OrderPlacement(Result result, int httpStatus, String code, String orderId, String detail) {

    public enum Result {
        // 2xx: the order exists
        ACCEPTED,
        // ORD-409: something already holds this idempotency key; whether it is our order is checked
        DUPLICATE,
        // any other 4xx: the Trade API refused it and no order exists
        REJECTED,
        // 5xx, timeout or no connection: the order may or may not exist
        UNCERTAIN
    }

    public static OrderPlacement accepted(int httpStatus, String orderId) {
        return new OrderPlacement(Result.ACCEPTED, httpStatus, null, orderId, "Order accepted by the Trade API");
    }

    public static OrderPlacement duplicate(String code, String detail) {
        return new OrderPlacement(Result.DUPLICATE, 409, code, null, detail);
    }

    public static OrderPlacement rejected(int httpStatus, String code, String detail) {
        return new OrderPlacement(Result.REJECTED, httpStatus, code, null, detail);
    }

    public static OrderPlacement uncertain(int httpStatus, String detail) {
        return new OrderPlacement(Result.UNCERTAIN, httpStatus, null, null, detail);
    }
}
