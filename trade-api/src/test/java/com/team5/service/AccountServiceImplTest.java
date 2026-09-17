package java.com.team5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.OrderStatus;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.dto.response.AccountResponse;
import com.team5.dto.response.BalanceResponse;
import com.team5.dto.response.OrderHistoryEntry;
import com.team5.dto.response.PositionResponse;
import com.team5.mapper.AccountQueryMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountQueryMapper accountQueryMapper;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountQueryMapper);
    }

    @Test
    void getAccount_shouldReturnDtoWhenAccountIsActive() {
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

        AccountResponse expected = new AccountResponse(
            101L,
            "ACC-000101",
            "Priya Menon",
            BigDecimal.valueOf(95000.00),
            AccountStatus.ACTIVE,
            7,
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );
        when(accountQueryMapper.getAccount(101L)).thenReturn(expected);

        AccountResponse actual = accountService.getAccount(101L);

        assertSame(expected, actual);
        verify(accountQueryMapper).getAccount(101L);
    }

    @Test
    void getBalance_shouldReturnDtoWhenAccountIsActive() {
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

        BalanceResponse expected = new BalanceResponse(
            101L,
            BigDecimal.valueOf(95000.00),
            "INR",
            OffsetDateTime.parse("2026-09-09T14:00:00Z")
        );
        when(accountQueryMapper.getBalance(101L)).thenReturn(expected);

        BalanceResponse actual = accountService.getBalance(101L);

        assertSame(expected, actual);
        verify(accountQueryMapper).getBalance(101L);
    }

    @Test
    void getPositions_shouldReturnDtoWhenAccountIsActive() {
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

        List<PositionResponse> expected = List.of(new PositionResponse(101L, "INFY.NS", 100, BigDecimal.valueOf(1450.00)));
        when(accountQueryMapper.getPositions(101L)).thenReturn(expected);

        List<PositionResponse> actual = accountService.getPositions(101L);

        assertSame(expected, actual);
        verify(accountQueryMapper).getPositions(101L);
    }

    @Test
    void getOrders_shouldReturnDtoWhenAccountIsActive() {
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

        List<OrderHistoryEntry> expected = List.of();
        when(accountQueryMapper.getOrders(101L, "FILLED", null, null)).thenReturn(expected);

        List<OrderHistoryEntry> actual = accountService.getOrders(101L, OrderStatus.FILLED, null, null);

        assertSame(expected, actual);
        verify(accountQueryMapper).getOrders(101L, "FILLED", null, null);
    }

    @Test
    void readEndpoints_shouldThrow404WhenAccountDoesNotExist() {
        when(accountQueryMapper.findAccountForOrder(999L)).thenReturn(null);

        AccountNotFoundException exception = assertThrows(
            AccountNotFoundException.class,
            () -> accountService.getAccount(999L)
        );

        assertEquals("ACC-404", exception.getCode());
    }

    @Test
    void readEndpoints_shouldThrow403WhenAccountIsInactive() {
        when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "SUSPENDED"));

        AccountNotActiveException exception = assertThrows(
            AccountNotActiveException.class,
            () -> accountService.getOrders(101L, null, null, null)
        );

        assertEquals("ACC-403", exception.getCode());
    }
}
