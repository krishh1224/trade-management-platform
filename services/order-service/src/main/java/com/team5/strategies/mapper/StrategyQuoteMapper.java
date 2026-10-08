package com.team5.strategies.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StrategyQuoteMapper {

    // the newest stored quote for the symbol, or null
    StoredQuote findLatest(@Param("symbol") String symbol);

    int insert(
        @Param("symbol") String symbol,
        @Param("quotedAt") OffsetDateTime quotedAt,
        @Param("price") BigDecimal price,
        @Param("eventId") UUID eventId
    );

    // up to `limit` prices for the symbol quoted at or before `upTo`, newest first
    List<BigDecimal> findRecentPrices(
        @Param("symbol") String symbol,
        @Param("upTo") OffsetDateTime upTo,
        @Param("limit") int limit
    );

    // up to `limit` stored quotes for the symbol, newest first, with their times (for the chart)
    List<StoredQuote> findRecentQuotes(@Param("symbol") String symbol, @Param("limit") int limit);

    // keeps the newest `keep` quotes for the symbol and deletes the rest
    int prune(@Param("symbol") String symbol, @Param("keep") int keep);

    class StoredQuote {
        private OffsetDateTime quotedAt;
        private UUID eventId;
        private BigDecimal price;

        public OffsetDateTime getQuotedAt() { return quotedAt; }
        public void setQuotedAt(OffsetDateTime quotedAt) { this.quotedAt = quotedAt; }

        public UUID getEventId() { return eventId; }
        public void setEventId(UUID eventId) { this.eventId = eventId; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}
