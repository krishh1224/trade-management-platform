package com.neueda.learning.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neueda.learning.client.FauxnanceQuoteClient.FauxnanceQuoteResponse;
import com.neueda.learning.client.FauxnanceQuoteClient.FauxnanceQuoteResponse.Data;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FauxnanceQuoteResponseTest {

    @Test
    void noArgsConstructorLeavesDataNull() {
        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse();

        assertNull(response.data);
    }

    @Test
    void allArgsConstructorAndToStringExposeData() {
        Data data = new Data(new BigDecimal("999.98"), new BigDecimal("1000.42"));
        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse(data);

        assertEquals(data, response.data);
        assertTrue(response.toString().contains("data=Data{"));
    }

    @Test
    void dataNoArgsConstructorLeavesFieldsNull() {
        Data data = new Data();

        assertNull(data.bid);
        assertNull(data.ask);
    }

    @Test
    void dataToStringContainsBidAndAsk() {
        Data data = new Data(new BigDecimal("999.98"), new BigDecimal("1000.42"));

        String text = data.toString();

        assertTrue(text.contains("bid=999.98"));
        assertTrue(text.contains("ask=1000.42"));
    }
}
