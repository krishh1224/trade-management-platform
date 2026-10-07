package com.team5.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyBatisCustomerPreferencesLookupTest {

    @Mock
    private PreferencesLookupMapper mapper;

    @Test
    void storedChannel_isReturned() {
        when(mapper.findStoredAlertChannel(101L)).thenReturn("SMS");

        assertEquals(Optional.of(AlertChannel.SMS), new MyBatisCustomerPreferencesLookup(mapper).findAlertChannel(101L));
    }

    @Test
    void storedNone_isNoneNotEmpty() {
        when(mapper.findStoredAlertChannel(101L)).thenReturn("NONE");

        assertEquals(Optional.of(AlertChannel.NONE), new MyBatisCustomerPreferencesLookup(mapper).findAlertChannel(101L));
    }

    @Test
    void noStoredRow_isEmpty() {
        when(mapper.findStoredAlertChannel(101L)).thenReturn(null);

        assertTrue(new MyBatisCustomerPreferencesLookup(mapper).findAlertChannel(101L).isEmpty());
    }
}
