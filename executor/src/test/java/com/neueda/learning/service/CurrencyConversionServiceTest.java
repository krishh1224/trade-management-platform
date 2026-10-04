package com.neueda.learning.service;

import com.neueda.learning.client.FauxnanceQuoteClient;
import com.neueda.learning.domain.Quote;
import com.neueda.learning.exception.QuoteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CurrencyConversionService. Rates are the live Fauxnance quotes observed on
 * 2026-10-04 (FX:USDINR bid 96.169038 / ask 96.170962).
 */
@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    private static final Quote USDINR = new Quote("FX:USDINR", new BigDecimal("96.169038"), new BigDecimal("96.170962"));

    @Mock
    private FauxnanceQuoteClient quoteClient;

    private CurrencyConversionService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyConversionService(quoteClient);
    }

    @Test
    void sameCurrency_ReturnsOneWithoutCallingFauxnance() {
        assertEquals(BigDecimal.ONE, service.rateFor("INR", "INR", "BUY"));
        assertEquals(BigDecimal.ONE, service.rateFor("inr ", "INR", "SELL"));
        assertFalse(service.requiresConversion("INR", "inr"));
        verify(quoteClient, never()).getFxRate(any(), any());
    }

    @Test
    void differentCurrency_RequiresConversion() {
        assertTrue(service.requiresConversion("USD", "INR"));
    }

    @Test
    void buy_UsesAskScaledToEightDecimals() {
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);

        assertEquals(new BigDecimal("96.17096200"), service.rateFor("USD", "INR", "BUY"));
    }

    @Test
    void sell_UsesBid() {
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);

        assertEquals(new BigDecimal("96.16903800"), service.rateFor("USD", "INR", "SELL"));
    }

    @Test
    void currencyCodesAreNormalisedBeforeLookup() {
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);

        service.rateFor(" usd", "Inr", "buy");

        verify(quoteClient).getFxRate("USD", "INR");
    }

    @Test
    void rateBeyondEightDecimals_IsRoundedHalfUp() {
        when(quoteClient.getFxRate("JPY", "INR")).thenReturn(
                new Quote("FX:JPYINR", new BigDecimal("0.6453212349"), new BigDecimal("0.6453212351")));

        assertEquals(new BigDecimal("0.64532124"), service.rateFor("JPY", "INR", "BUY"));
    }

    @Test
    void fxUnavailable_Propagates() {
        when(quoteClient.getFxRate("USD", "INR")).thenThrow(new QuoteUnavailableException("down"));

        assertThrows(QuoteUnavailableException.class, () -> service.rateFor("USD", "INR", "BUY"));
    }

    @Test
    void nonPositiveRate_IsTreatedAsUnavailable() {
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(
                new Quote("FX:USDINR", BigDecimal.ZERO, BigDecimal.ZERO));

        assertThrows(QuoteUnavailableException.class, () -> service.rateFor("USD", "INR", "BUY"));
    }

    @Test
    void unknownSide_IsRejected() {
        when(quoteClient.getFxRate("USD", "INR")).thenReturn(USDINR);

        assertThrows(IllegalArgumentException.class, () -> service.rateFor("USD", "INR", "HOLD"));
    }

    @Test
    void blankCurrency_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.rateFor(null, "INR", "BUY"));
        assertThrows(IllegalArgumentException.class, () -> service.rateFor("USD", " ", "BUY"));
    }

    @Test
    void convert_RoundsHalfUpToTwoDecimals() {
        // 667.46 USD x 96.170962 = 64190.27029652
        assertEquals(new BigDecimal("64190.27"),
                CurrencyConversionService.convert(new BigDecimal("667.46"), new BigDecimal("96.17096200")));
        // 0.125 x 1 -> 0.13 (half-up, not banker's rounding)
        assertEquals(new BigDecimal("0.13"),
                CurrencyConversionService.convert(new BigDecimal("0.125"), BigDecimal.ONE));
    }
}
