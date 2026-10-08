package com.team5.tradeapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.controller.OrderController;
import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.OrderStatus;
import com.team5.dto.response.OrderResponse;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.OrderNotFoundException;
import com.team5.domain.OrderIdentifiers;
import com.team5.kafka.OrderEventPublisher;
import com.team5.notifications.service.NotificationService;
import com.team5.service.OrderService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @MockBean
    private NotificationService notificationService;

    @Test
    void placeOrder_shouldReturnOrderResponse() throws Exception {
        when(orderService.placeOrder(eq(101L), any())).thenReturn(new OrderResponse(
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
            .with(authenticatedAs(101L))
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
            .with(authenticatedAs(101L))
                .content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"))
            .andExpect(jsonPath("$.message").value("Invalid input"));

        // an order that fails validation publishes nothing
        verifyNoInteractions(orderService, orderEventPublisher);
    }

    @Test
    void placeOrder_publishesOrderPlacedForAcceptedOrder() throws Exception {
        when(orderService.placeOrder(eq(101L), any())).thenReturn(new OrderResponse(
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
            .with(authenticatedAs(101L))
                .content(body))
            .andExpect(status().isOk());

        verify(orderEventPublisher).publishOrderPlaced(any(), any(), any());
    }

    @Test
    void placeOrder_publishesOnlyAfterServiceCallReturns() throws Exception {
        when(orderService.placeOrder(eq(101L), any())).thenReturn(new OrderResponse(
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
            .with(authenticatedAs(101L))
                .content(body))
            .andExpect(status().isOk());

        // published after the transaction commits — i.e. after the service call returns —
        // never before it, so a failed/rolled-back placeOrder() cannot yield a publish
        var callOrder = inOrder(orderService, orderEventPublisher);
        callOrder.verify(orderService).placeOrder(eq(101L), any());
        callOrder.verify(orderEventPublisher).publishOrderPlaced(any(), any(), any());
    }

    @Test
    void cancelOrder_shouldReturnCancelledOrder() throws Exception {
        UUID id = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        when(orderService.cancelOrder(eq(101L), eq(id))).thenReturn(new OrderResponse(
            "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
            OrderStatus.CANCELLED,
            "Order cancelled",
            "INFY.NS",
            OrderSide.BUY,
            10,
            BigDecimal.valueOf(1500.00)
        ));

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
            .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e"))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        // published only after the service (and its transaction) has returned
        var callOrder = inOrder(orderService, orderEventPublisher);
        callOrder.verify(orderService).cancelOrder(eq(101L), eq(id));
        callOrder.verify(orderEventPublisher).publishOrderCancelled(eq(101L), eq(id), any());

        // and the customer is told at once, with the order's own details
        verify(notificationService).recordCancelledOrder(
            101L, OrderIdentifiers.persistentId(id), "INFY.NS", "BUY", 10, BigDecimal.valueOf(1500.00));
    }

    @Test
    void cancelOrder_notificationFailure_stillReturnsTheCancelledOrder() throws Exception {
        UUID id = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        when(orderService.cancelOrder(eq(101L), eq(id))).thenReturn(new OrderResponse(
            "ORD-6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e", OrderStatus.CANCELLED, "Order cancelled",
            "INFY.NS", OrderSide.BUY, 10, BigDecimal.valueOf(1500.00)));
        when(notificationService.recordCancelledOrder(anyLong(), anyLong(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
            .with(authenticatedAs(101L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_rejected_shouldNotPublishOrderCancelled() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrder(eq(101L), eq(id))).thenThrow(new AccountAccessDeniedException());

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
                .with(authenticatedAs(101L)))
            .andExpect(status().isForbidden());

        verifyNoInteractions(orderEventPublisher, notificationService);
    }

    @Test
    void cancelOrder_withoutToken_shouldReturn401AndNotTouchService() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeOrder_withoutToken_shouldReturn401AndNotTouchService() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"));

        verifyNoInteractions(orderService);
    }

    @Test
    void cancelOrder_ownedByAnotherAccount_shouldReturn403() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrder(eq(101L), eq(id))).thenThrow(new AccountAccessDeniedException());

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
                .with(authenticatedAs(101L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"))
            .andExpect(jsonPath("$.message").value("Account not active"));
    }

    @Test
    void cancelOrder_unknownOrder_shouldReturn404PerContract() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrder(eq(101L), eq(id))).thenThrow(new OrderNotFoundException());

        mockMvc.perform(delete("/api/v1/orders/{id}", id)
                .with(authenticatedAs(101L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORD-409"))
            .andExpect(jsonPath("$.message").value("Order not found"));
    }

    private static JwtRequestPostProcessor authenticatedAs(long accountId) {
        return jwt().jwt(token -> token.claim("accountId", accountId));
    }
}
