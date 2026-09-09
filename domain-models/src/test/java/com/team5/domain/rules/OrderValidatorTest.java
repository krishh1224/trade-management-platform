
package com.team5.domain.rules;

import com.team5.domain.Account;
import com.team5.domain.Instrument;
import com.team5.domain.PortfolioHolding;
import com.team5.domain.enums.AccountStatus;
import com.team5.domain.enums.Exchange;
import com.team5.domain.enums.OrderSide;
import com.team5.domain.enums.RiskProfile;
import com.team5.domain.exception.AccountNotActiveException;
import com.team5.domain.exception.AccountNotFoundException;
import com.team5.domain.exception.DuplicateOrderException;
import com.team5.domain.exception.InstrumentNotFoundException;
import com.team5.domain.exception.InsufficientFundsException;
import com.team5.domain.exception.InsufficientHoldingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    private OrderValidator validator;
    private InMemoryIdempotencyChecker idempotencyChecker;

    @BeforeEach
    void setUp() {
        idempotencyChecker = new InMemoryIdempotencyChecker();
        validator = new OrderValidator(idempotencyChecker);
    }

    private Account activeAccount(BigDecimal balance) {
        return new Account(1L, 1L, "ACC-0001", RiskProfile.MODERATE,
                "USD", balance, AccountStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private Instrument tradableInstrument() {
        return new Instrument(1L, "FAUX", "Fauxnance Corp",
                "EQUITY", "USD", Exchange.NSE, true);
    }

    private PortfolioHolding holdingOf(BigDecimal quantity) {
        return new PortfolioHolding(1L, 1L, 1L,
                quantity, new BigDecimal("10.00"),
                LocalDateTime.now(), LocalDateTime.now());
    }

    // Checks that a valid BUY order passes all the rules.
    @Test
    void buySucceedsWhenAllRulesPass() {
        Account account = activeAccount(new BigDecimal("1000.00"));
        Instrument instrument = tradableInstrument();

        validator.validate(account, instrument, null, OrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("50.00"), "key-buy-1");
    }

    // Checks that a BUY is rejected when the account does not have enough money.
    @Test
    void buyRejectedOnInsufficientFunds() {
        Account account = activeAccount(new BigDecimal("100.00"));
        Instrument instrument = tradableInstrument();

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () ->
                validator.validate(account, instrument, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-buy-2"));

        assertEquals("ORD-400", ex.getCode());
    }

    // Checks that a SELL is rejected when the account does not have enough holdings.
    @Test
    void sellRejectedOnInsufficientHoldings() {
        Account account = activeAccount(new BigDecimal("1000.00"));
        Instrument instrument = tradableInstrument();
        PortfolioHolding holding = holdingOf(new BigDecimal("5"));

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class, () ->
                validator.validate(account, instrument, holding, OrderSide.SELL,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-sell-1"));

        assertEquals("ORD-409", ex.getCode());
    }

    // Checks that an order is rejected when the account does not exist.
    @Test
    void orderRejectedWhenAccountDoesNotExist() {
        Instrument instrument = tradableInstrument();

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class, () ->
                validator.validate(null, instrument, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-4"));

        assertEquals("ACC-404", ex.getCode());
    }

    // Checks that an order is rejected when the account is inactive.
    @Test
    void orderRejectedWhenAccountInactive() {
        Account account = new Account(1L, 1L, "ACC-0002", RiskProfile.MODERATE,
                "USD", new BigDecimal("1000.00"), AccountStatus.SUSPENDED,
                LocalDateTime.now(), LocalDateTime.now());
        Instrument instrument = tradableInstrument();

        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class, () ->
                validator.validate(account, instrument, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-5"));

        assertEquals("ACC-403", ex.getCode());
    }

    // Checks that an order is rejected when the instrument does not exist.
    @Test
    void orderRejectedWhenInstrumentUnknown() {
        Account account = activeAccount(new BigDecimal("1000.00"));

        InstrumentNotFoundException ex = assertThrows(InstrumentNotFoundException.class, () ->
                validator.validate(account, null, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-6a"));
        assertEquals("INS-404", ex.getCode());
    }

    // Checks that an order is rejected when the instrument cannot be traded.
    @Test
    void orderRejectedWhenInstrumentNotTradable() {
        Account account = activeAccount(new BigDecimal("1000.00"));
        Instrument delisted = tradableInstrument();
        delisted.delist();

        assertThrows(InstrumentNotFoundException.class, () ->
                validator.validate(account, delisted, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-6b"));
    }

    // Checks that an order is rejected when the quantity is zero, negative, or missing.
@Test
void orderRejectedWhenQuantityIsInvalid() {
    Account account = activeAccount(new BigDecimal("1000.00"));
    Instrument instrument = tradableInstrument();

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    BigDecimal.ZERO, new BigDecimal("50.00"), "key-qty-1"));

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    new BigDecimal("-10"), new BigDecimal("50.00"), "key-qty-2"));

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    null, new BigDecimal("50.00"), "key-qty-3"));
}

// Checks that an order is rejected when the price is zero, negative, or missing.
@Test
void orderRejectedWhenPriceIsInvalid() {
    Account account = activeAccount(new BigDecimal("1000.00"));
    Instrument instrument = tradableInstrument();

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    new BigDecimal("10"), BigDecimal.ZERO, "key-price-1"));

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    new BigDecimal("10"), new BigDecimal("-50.00"), "key-price-2"));

    assertThrows(IllegalArgumentException.class, () ->
            validator.validate(account, instrument, null, OrderSide.BUY,
                    new BigDecimal("10"), null, "key-price-3"));
}

    // Checks that an order is rejected when the idempotency key is already used.
    @Test
    void duplicateIdempotencyKeyRejected() {
        Account account = activeAccount(new BigDecimal("1000.00"));
        Instrument instrument = tradableInstrument();
        idempotencyChecker.record("key-7");

        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class, () ->
                validator.validate(account, instrument, null, OrderSide.BUY,
                        new BigDecimal("10"), new BigDecimal("50.00"), "key-7"));

        assertEquals("ORD-409", ex.getCode());
    }

   
}

