package com.team5.portfolio.candles;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

public enum CandleRange {
    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12);

    private final String code;
    private final int months;

    CandleRange(String code, int months) {
        this.code = code;
        this.months = months;
    }

    public String code() {
        return code;
    }

    /** First date included when the series ends on {@code lastDate}. */
    public LocalDate startingFrom(LocalDate lastDate) {
        return lastDate.minusMonths(months);
    }

    public static Optional<CandleRange> fromCode(String code) {
        return Arrays.stream(values()).filter(range -> range.code.equals(code)).findFirst();
    }
}
