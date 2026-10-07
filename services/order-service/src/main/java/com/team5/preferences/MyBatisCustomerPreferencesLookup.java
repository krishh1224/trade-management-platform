package com.team5.preferences;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MyBatisCustomerPreferencesLookup implements CustomerPreferencesLookup {

    private static final Logger log = LoggerFactory.getLogger(MyBatisCustomerPreferencesLookup.class);

    private final PreferencesLookupMapper preferencesLookupMapper;

    public MyBatisCustomerPreferencesLookup(PreferencesLookupMapper preferencesLookupMapper) {
        this.preferencesLookupMapper = preferencesLookupMapper;
    }

    @Override
    public Optional<AlertChannel> findAlertChannel(long accountId) {
        String stored = preferencesLookupMapper.findStoredAlertChannel(accountId);
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AlertChannel.valueOf(stored.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            // the column is an enum, so this means the enum and this class have drifted apart
            log.error("Unrecognised alert channel '{}' stored for account {}", stored, accountId);
            return Optional.empty();
        }
    }
}
