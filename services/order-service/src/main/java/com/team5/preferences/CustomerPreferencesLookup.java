package com.team5.preferences;

import java.util.Optional;

// In-process interface other modules use to read a customer's preferences.
// No token is involved: callers are inside the Trade REST API (e.g. the notifications dispatcher)
// and are responsible for only acting on the account the event or alert belongs to.
public interface CustomerPreferencesLookup {

    // Empty when the account has no stored preference row. A stored NONE comes back as NONE,
    // so callers can tell "opted out" apart from "never chose".
    Optional<AlertChannel> findAlertChannel(long accountId);
}
