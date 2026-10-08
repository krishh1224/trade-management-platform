package com.team5.strategies.service;

import static com.team5.strategies.StrategyFixtures.OWNER;
import static com.team5.strategies.StrategyFixtures.T0;
import static com.team5.strategies.StrategyFixtures.defaultProperties;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.service.AccountService;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.domain.StrategyType;
import com.team5.strategies.dto.CreateStrategyRequest;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.exception.InvalidStrategyException;
import com.team5.strategies.exception.StrategyNotFoundException;
import com.team5.strategies.mapper.StrategyExecutionMapper;
import com.team5.strategies.mapper.StrategyMapper;
import com.team5.strategies.mapper.StrategyReadMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StrategyServiceTest {

    private static final long OTHER = 202L;

    private StrategyMapper strategyMapper;
    private StrategyReadMapper readMapper;
    private AccountService accountService;
    private StrategyService service;

    @BeforeEach
    void setUp() {
        strategyMapper = mock(StrategyMapper.class);
        readMapper = mock(StrategyReadMapper.class);
        accountService = mock(AccountService.class);
        service = new StrategyService(strategyMapper, mock(StrategyExecutionMapper.class), readMapper, accountService,
            new AccountAccessAuthorizer(), defaultProperties(), Clock.fixed(T0.toInstant(), ZoneOffset.UTC));
        when(readMapper.findTradableSymbol("INFY.NS")).thenReturn("INFY.NS");
    }

    @Test
    void create_storesAnEnabledStrategyForTheTokensAccount() {
        doAnswer(invocation -> {
            invocation.<StrategyRow>getArgument(0).setStrategyId(9L);
            return 1;
        }).when(strategyMapper).insert(any());
        when(strategyMapper.findForAccount(9L, OWNER)).thenReturn(maCrossover(9L));

        StrategyResponse created = service.create(OWNER, OWNER, maRequest());

        ArgumentCaptor<StrategyRow> row = ArgumentCaptor.forClass(StrategyRow.class);
        verify(strategyMapper).insert(row.capture());
        assertEquals(OWNER, row.getValue().getAccountId());
        assertEquals(StrategyStatus.ENABLED, row.getValue().getStatus());
        assertEquals(3, row.getValue().getMaxConsecutiveFailures());
        // serialised against a concurrent create before counting
        verify(strategyMapper).lockAccountStrategies(OWNER);
        assertEquals(9L, created.strategyId());
    }

    @Test
    void create_forAnotherAccount_isAcc403_andWritesNothing() {
        assertThrows(AccountAccessDeniedException.class, () -> service.create(OWNER, OTHER, maRequest()));
        verify(strategyMapper, never()).insert(any());
    }

    @Test
    void create_onAnInactiveAccount_writesNothing() {
        when(accountService.getAccount(OWNER, OWNER)).thenThrow(new AccountNotActiveException());

        assertThrows(AccountNotActiveException.class, () -> service.create(OWNER, OWNER, maRequest()));
        verify(strategyMapper, never()).insert(any());
    }

    @Test
    void create_onAnUntradableSymbol_isIns404() {
        CreateStrategyRequest request = maRequest();
        request.setSymbol("NOPE");

        assertThrows(InstrumentNotFoundException.class, () -> service.create(OWNER, OWNER, request));
        verify(strategyMapper, never()).insert(any());
    }

    @Test
    void create_beyondTheCap_isRefused() {
        when(strategyMapper.countByAccount(OWNER)).thenReturn(10);

        InvalidStrategyException e = assertThrows(InvalidStrategyException.class,
            () -> service.create(OWNER, OWNER, maRequest()));
        assertEquals("Strategy limit reached", e.getMessage());
        verify(strategyMapper, never()).insert(any());
    }

    @Test
    void validate_rejectsParametersThatDoNotFitTheType() {
        CreateStrategyRequest crossedWindows = maRequest();
        crossedWindows.setShortWindow(20);
        crossedWindows.setLongWindow(5);
        assertEquals("shortWindow must be smaller than longWindow", invalid(crossedWindows));

        CreateStrategyRequest missingWindow = maRequest();
        missingWindow.setLongWindow(null);
        assertEquals("A moving-average crossover needs shortWindow and longWindow", invalid(missingWindow));

        CreateStrategyRequest mixed = maRequest();
        mixed.setBandWidth(BigDecimal.ONE);
        assertEquals("bandWindow and bandWidth apply to Bollinger bands only", invalid(mixed));

        CreateStrategyRequest bands = maRequest();
        bands.setType(StrategyType.BOLLINGER_BANDS);
        bands.setShortWindow(null);
        bands.setLongWindow(null);
        bands.setBandWindow(20);
        assertEquals("Bollinger bands need bandWindow and bandWidth", invalid(bands));

        CreateStrategyRequest uselessPosition = maRequest();
        uselessPosition.setMaxPosition(5L);
        assertEquals("maxPosition must be at least quantity, or no BUY could ever be placed", invalid(uselessPosition));
    }

    @Test
    void disable_switchesTheStrategyOff() {
        when(strategyMapper.findOwner(9L)).thenReturn(OWNER);
        when(strategyMapper.findForAccount(9L, OWNER)).thenReturn(maCrossover(9L));

        service.disable(OWNER, OWNER, 9L);

        verify(strategyMapper).updateStatus(eq(9L), eq(OWNER), eq(StrategyStatus.DISABLED),
            eq("Disabled by the customer"), eq(false), any());
    }

    @Test
    void enable_clearsTheFailureRunThatHaltedIt() {
        when(strategyMapper.findOwner(9L)).thenReturn(OWNER);
        when(strategyMapper.findForAccount(9L, OWNER)).thenReturn(maCrossover(9L));

        service.enable(OWNER, OWNER, 9L);

        verify(accountService).getAccount(OWNER, OWNER);
        verify(strategyMapper).updateStatus(eq(9L), eq(OWNER), eq(StrategyStatus.ENABLED), isNull(), eq(true), any());
    }

    @Test
    void anotherAccountsStrategy_isAcc403_andNothingChanges() {
        when(strategyMapper.findOwner(9L)).thenReturn(OTHER);

        assertThrows(AccountAccessDeniedException.class, () -> service.disable(OWNER, OWNER, 9L));
        assertThrows(AccountAccessDeniedException.class, () -> service.delete(OWNER, OWNER, 9L));
        verify(strategyMapper, never()).updateStatus(anyLong(), anyLong(), any(), any(), anyBoolean(), any());
        verify(strategyMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    void unknownStrategy_isStr404() {
        when(strategyMapper.findOwner(9L)).thenReturn(null);

        assertThrows(StrategyNotFoundException.class, () -> service.get(OWNER, OWNER, 9L));
    }

    private String invalid(CreateStrategyRequest request) {
        return assertThrows(InvalidStrategyException.class, () -> StrategyService.validate(request)).getMessage();
    }

    static CreateStrategyRequest maRequest() {
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setName("INFY trend");
        request.setSymbol("INFY.NS");
        request.setType(StrategyType.MA_CROSSOVER);
        request.setShortWindow(5);
        request.setLongWindow(20);
        request.setQuantity(10);
        request.setMaxSpend(new BigDecimal("100000.00"));
        request.setMaxPosition(50L);
        return request;
    }
}
