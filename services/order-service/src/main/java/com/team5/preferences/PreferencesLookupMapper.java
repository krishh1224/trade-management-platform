package com.team5.preferences;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PreferencesLookupMapper {

    // null when no customer_preferences row exists; deliberately no COALESCE
    String findStoredAlertChannel(@Param("accountId") long accountId);
}
