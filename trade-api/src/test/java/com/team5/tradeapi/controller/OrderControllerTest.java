package com.team5.tradeapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.controller.OrderController;
import com.team5.auth.AccountAccessAuthorizer;
import com.team5.auth.JwtTokenVerifier;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.OrderResponse;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final String AUTHENTICATED_ACCOUNT_ID = "authenticatedAccountId";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private AccountAccessAuthorizer accountAccessAuthorizer;

    @MockBean
    private JwtTokenVerifier jwtTokenVerifier;

    @Test
    void placeOrder_shouldReturnOrderResponse() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(new OrderResponse(
            "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
            OrderStatus.NEW,
            "Order accepted",
            "INFY.NS",
            OrderSide.BUY,
            10,
            BigDecimal.valueOf(1500.00)
        ));

        String body = """
            {
              "accountId": 101,
              "symbol": "INFY.NS",
              "side": "BUY",
              "quantity": 10,
              "price": 1500.00,
              "idempotencyKey": "order-123456"
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"))
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.message").value("Order accepted"))
            .andExpect(jsonPath("$.symbol").value("INFY.NS"))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.quantity").value(10))
            .andExpect(jsonPath("$.price").value(1500.0));
    }

    @Test
    void placeOrder_shouldReturn422ForInvalidPayload() throws Exception {
        String body = """
            {
              "accountId": 0,
              "symbol": "",
              "side": "BUY",
              "quantity": 0,
              "price": 0,
              "idempotencyKey": "short"
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L)
                .content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"))
            .andExpect(jsonPath("$.message").value("Invalid input"));
    }

    @Test
    void cancelOrder_shouldReturnCancelledOrder() throws Exception {
        UUID id = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        when(orderService.cancelOrder(eq(id))).thenReturn(new OrderResponse(
            "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
            OrderStatus.CANCELLED,
            "Order cancelled",
            "INFY.NS",
            OrderSide.BUY,
            10,
            BigDecimal.valueOf(1500.00)
        ));

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
            .requestAttr(AUTHENTICATED_ACCOUNT_ID, 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
