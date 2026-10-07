            package com.team5.service;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertSame;
            import static org.junit.jupiter.api.Assertions.assertThrows;
            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.ArgumentMatchers.eq;
            import static org.mockito.ArgumentMatchers.isNull;
            import static org.mockito.Mockito.doThrow;
            import static org.mockito.Mockito.never;
            import static org.mockito.Mockito.times;
            import static org.mockito.Mockito.verify;
            import static org.mockito.Mockito.verifyNoInteractions;
            import static org.mockito.Mockito.when;

            import com.team5.auth.AccountAccessAuthorizer;
            import com.team5.domain.enums.AccountStatus;
            import com.team5.domain.enums.OrderStatus;
            import com.team5.domain.enums.TransactionType;
            import com.team5.domain.exception.AccountAccessDeniedException;
            import com.team5.domain.exception.AccountNotActiveException;
            import com.team5.domain.exception.AccountNotFoundException;
            import com.team5.domain.exception.CashMovementConflictException;
            import com.team5.domain.exception.WithdrawalExceedsAvailableException;
            import com.team5.dto.request.CashMovementRequest;
            import com.team5.dto.request.UpdatePreferencesRequest;
            import com.team5.dto.response.AccountResponse;
            import com.team5.dto.response.BalanceResponse;
            import com.team5.dto.response.CashTransactionResponse;
            import com.team5.dto.response.CustomerPreferencesResponse;
            import com.team5.dto.response.OrderHistoryEntry;
            import com.team5.dto.response.PositionResponse;
            import com.team5.mapper.AccountQueryMapper;
            import com.team5.mapper.TransactionMapper;
            import org.springframework.dao.DuplicateKeyException;
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

                @Mock
                private TransactionMapper transactionMapper;

                @Mock
                private AccountAccessAuthorizer accountAccessAuthorizer;

                private AccountServiceImpl accountService;

                @BeforeEach
                void setUp() {
                    accountService = new AccountServiceImpl(accountQueryMapper, transactionMapper, accountAccessAuthorizer);
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

                    AccountResponse actual = accountService.getAccount(101L, 101L);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).getAccount(101L);
                }

                @Test
                void getBalance_shouldReturnDtoWhenAccountIsActive() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

                    BalanceResponse expected = new BalanceResponse(
                        101L,
                        BigDecimal.valueOf(95000.00),
                        BigDecimal.valueOf(5000.00),
                        BigDecimal.valueOf(90000.00),
                        "INR",
                        OffsetDateTime.parse("2026-09-09T14:00:00Z")
                    );
                    when(accountQueryMapper.getBalance(101L)).thenReturn(expected);

                    BalanceResponse actual = accountService.getBalance(101L, 101L);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).getBalance(101L);
                }

                @Test
                void getPositions_shouldReturnDtoWhenAccountIsActive() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

                    List<PositionResponse> expected = List.of(new PositionResponse(101L, "INFY.NS", 100, BigDecimal.valueOf(1450.00)));
                    when(accountQueryMapper.getPositions(101L)).thenReturn(expected);

                    List<PositionResponse> actual = accountService.getPositions(101L, 101L);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).getPositions(101L);
                }

                @Test
                void getOrders_shouldReturnDtoWhenAccountIsActive() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));

                    List<OrderHistoryEntry> expected = List.of();
                    when(accountQueryMapper.getOrders(101L, "FILLED", null, null)).thenReturn(expected);

                    List<OrderHistoryEntry> actual = accountService.getOrders(101L, 101L, OrderStatus.FILLED, null, null);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).getOrders(101L, "FILLED", null, null);
                }

                @Test
                void readEndpoints_shouldThrow404WhenAccountDoesNotExist() {
                    when(accountQueryMapper.findAccountForOrder(999L)).thenReturn(null);

                    AccountNotFoundException exception = assertThrows(
                        AccountNotFoundException.class,
                        () -> accountService.getAccount(101L, 999L)
                    );

                    assertEquals("ACC-404", exception.getCode());
                }

                @Test
                void readEndpoints_shouldThrow403WhenAccountIsInactive() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "SUSPENDED"));

                    AccountNotActiveException exception = assertThrows(
                        AccountNotActiveException.class,
                        () -> accountService.getOrders(101L, 101L, null, null, null)
                    );

                    assertEquals("ACC-403", exception.getCode());
                }

                @Test
                void deposit_shouldIncreaseBalanceAndRecordLedgerRow() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.updateBalanceWithVersion(eq(101L), eq(new BigDecimal("1500.00")), eq(3L), any()))
                        .thenReturn(1);

                    CashTransactionResponse response = accountService.deposit(101L, 101L, request("500", "deposit-key-1"));

                    assertEquals(TransactionType.DEPOSIT, response.type());
                    assertEquals(new BigDecimal("500.00"), response.amount());
                    assertEquals(new BigDecimal("1000.00"), response.balanceBefore());
                    assertEquals(new BigDecimal("1500.00"), response.balanceAfter());
                    assertEquals("INR", response.currency());
                    verify(transactionMapper).insertTransaction(
                        any(), eq(101L), isNull(), isNull(), eq("DEPOSIT"), isNull(), isNull(),
                        eq(new BigDecimal("500.00")), eq("INR"), eq(new BigDecimal("1000.00")),
                        eq(new BigDecimal("1500.00")), eq("deposit-key-1"), any());
                    verify(accountQueryMapper, never()).sumHeldForNewBuyOrders(any());
                }

                @Test
                void withdraw_shouldDecreaseBalanceWhenWithinAvailable() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.sumHeldForNewBuyOrders(101L)).thenReturn(BigDecimal.ZERO);
                    when(accountQueryMapper.updateBalanceWithVersion(eq(101L), eq(new BigDecimal("0.00")), eq(3L), any()))
                        .thenReturn(1);

                    CashTransactionResponse response = accountService.withdraw(101L, 101L, request("1000.00", "withdraw-key-1"));

                    assertEquals(TransactionType.WITHDRAWAL, response.type());
                    assertEquals(new BigDecimal("0.00"), response.balanceAfter());
                }

                @Test
                void withdraw_shouldRejectAmountAboveBalance() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.sumHeldForNewBuyOrders(101L)).thenReturn(BigDecimal.ZERO);

                    WithdrawalExceedsAvailableException exception = assertThrows(
                        WithdrawalExceedsAvailableException.class,
                        () -> accountService.withdraw(101L, 101L, request("1000.01", "withdraw-key-2"))
                    );

                    assertEquals("CSH-400", exception.getCode());
                    verify(accountQueryMapper, never()).updateBalanceWithVersion(any(), any(), any(), any());
                    verifyNoInteractions(transactionMapper);
                }

                @Test
                void withdraw_shouldNotReleaseCashHeldForNewBuyOrders() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.sumHeldForNewBuyOrders(101L)).thenReturn(new BigDecimal("400.00"));

                    assertThrows(
                        WithdrawalExceedsAvailableException.class,
                        () -> accountService.withdraw(101L, 101L, request("600.01", "withdraw-key-3"))
                    );
                    verifyNoInteractions(transactionMapper);
                }

                @Test
                void moveCash_shouldRetryOnceThenReportConflictWhenVersionKeepsChanging() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.updateBalanceWithVersion(eq(101L), any(), eq(3L), any())).thenReturn(0);

                    CashMovementConflictException exception = assertThrows(
                        CashMovementConflictException.class,
                        () -> accountService.deposit(101L, 101L, request("10", "deposit-key-2"))
                    );

                    assertEquals("CSH-409", exception.getCode());
                    verify(accountQueryMapper, times(2)).updateBalanceWithVersion(eq(101L), any(), eq(3L), any());
                    verifyNoInteractions(transactionMapper);
                }

                @Test
                void moveCash_shouldReportConflictWhenIdempotencyKeyWasUsed() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(activeAccount("1000.00", 3L));
                    when(accountQueryMapper.updateBalanceWithVersion(eq(101L), any(), eq(3L), any())).thenReturn(1);
                    when(transactionMapper.insertTransaction(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenThrow(new DuplicateKeyException("transactions_idempotency_key_key"));

                    CashMovementConflictException exception = assertThrows(
                        CashMovementConflictException.class,
                        () -> accountService.deposit(101L, 101L, request("10", "used-key-1"))
                    );

                    assertEquals("CSH-409", exception.getCode());
                }

                @Test
                void moveCash_shouldRejectAnotherAccount() {
                    doThrow(new AccountAccessDeniedException()).when(accountAccessAuthorizer).assertAuthorised(101L, 202L);

                    assertThrows(
                        AccountAccessDeniedException.class,
                        () -> accountService.deposit(101L, 202L, request("10", "deposit-key-3"))
                    );
                    verifyNoInteractions(transactionMapper);
                }

                @Test
                void getCashTransactions_shouldReturnLedgerWhenAccountIsActive() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));
                    List<CashTransactionResponse> expected = List.of();
                    when(transactionMapper.selectCashTransactions(101L, 20)).thenReturn(expected);

                    assertSame(expected, accountService.getCashTransactions(101L, 101L, 20));
                }

                @Test
                void getPreferences_shouldReturnCurrentPreferences() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));
                    CustomerPreferencesResponse expected = new CustomerPreferencesResponse("INR", "LIGHT", "EMAIL");
                    when(accountQueryMapper.getPreferences(101L)).thenReturn(expected);

                    CustomerPreferencesResponse actual = accountService.getPreferences(101L, 101L);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).getPreferences(101L);
                }

                @Test
                void updatePreferences_shouldUpsertAndReturnUpdatedPreferences() {
                    when(accountQueryMapper.findAccountForOrder(101L)).thenReturn(Map.of("status", "ACTIVE"));
                    CustomerPreferencesResponse expected = new CustomerPreferencesResponse("USD", "DARK", "SMS");
                    when(accountQueryMapper.getPreferences(101L)).thenReturn(expected);

                    UpdatePreferencesRequest request = new UpdatePreferencesRequest();
                    request.setCurrency("USD");
                    request.setTheme("DARK");
                    request.setAlertChannel("SMS");

                    CustomerPreferencesResponse actual = accountService.updatePreferences(101L, 101L, request);

                    assertSame(expected, actual);
                    verify(accountQueryMapper).upsertPreferences(101L, "USD", "DARK", "SMS");
                    verify(accountQueryMapper).getPreferences(101L);
                }

                private static Map<String, Object> activeAccount(String balance, long version) {
                    return Map.of(
                        "status", "ACTIVE",
                        "balance", new BigDecimal(balance),
                        "base_currency", "INR",
                        "version", version
                    );
                }

                private static CashMovementRequest request(String amount, String idempotencyKey) {
                    CashMovementRequest request = new CashMovementRequest();
                    request.setAmount(new BigDecimal(amount));
                    request.setIdempotencyKey(idempotencyKey);
                    return request;
                }
            }
