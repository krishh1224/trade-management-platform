package com.team5.watchlists.mapper;

import com.team5.notifications.api.AlertDirection;
import com.team5.watchlists.domain.PriceAlertRow;
import com.team5.watchlists.dto.PriceAlertResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PriceAlertMapper {

    // Serialises alert creation per account until the transaction ends, so two concurrent creates
    // cannot both pass the active-alert limit. Returns 1.
    Integer lockAccountAlerts(@Param("accountId") long accountId);

    int countActiveByAccount(@Param("accountId") long accountId);

    // an ACTIVE alert of this account at this level already exists (uq_price_alerts_active_level)
    boolean existsActive(
        @Param("accountId") long accountId,
        @Param("symbol") String symbol,
        @Param("direction") AlertDirection direction,
        @Param("threshold") BigDecimal threshold
    );

    // sets alertId on the row
    int insert(PriceAlertRow alert);

    List<PriceAlertResponse> findByAccount(@Param("accountId") long accountId);

    PriceAlertResponse findForAccount(@Param("alertId") long alertId, @Param("accountId") long accountId);

    // the owning account, whoever asks; null when the alert does not exist
    Long findOwner(@Param("alertId") long alertId);

    int deleteForAccount(@Param("alertId") long alertId, @Param("accountId") long accountId);

    // The per-quote lookup, on the partial index (symbol, threshold) WHERE status = 'ACTIVE': only the
    // symbol's active alerts whose threshold lies between the previous and the current price.
    List<PriceAlertRow> findActiveBySymbolBetween(
        @Param("symbol") String symbol,
        @Param("low") BigDecimal low,
        @Param("high") BigDecimal high
    );

    // ACTIVE -> TRIGGERED. Returns 0 when the alert was already triggered or deleted, so a crossing
    // seen twice is handed to notifications at most once.
    int markTriggered(
        @Param("alertId") long alertId,
        @Param("triggerPrice") BigDecimal triggerPrice,
        @Param("triggeredAt") OffsetDateTime triggeredAt
    );
}
