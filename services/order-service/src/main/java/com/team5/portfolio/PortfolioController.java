package com.team5.portfolio;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.portfolio.candles.CandleRange;
import com.team5.portfolio.candles.CandleService;
import com.team5.portfolio.candles.CandlesResponse;
import com.team5.portfolio.dto.PortfolioSummaryResponse;
import com.team5.portfolio.dto.PricedPositionResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CandleService candleService;

    public PortfolioController(PortfolioService portfolioService, CandleService candleService) {
        this.portfolioService = portfolioService;
        this.candleService = candleService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary(
        @PathVariable("accountId") @Min(1) Long accountId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(portfolioService.getSummary(authenticatedAccountId, accountId));
    }

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<PricedPositionResponse>> getPricedPositions(
        @PathVariable("accountId") @Min(1) Long accountId,
        @RequestParam(value = "symbol", required = false) @Size(max = 20) String symbol,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(portfolioService.getPositions(authenticatedAccountId, accountId, symbol));
    }

    /** Daily candles for a held instrument. Not part of portfolio-api.yaml; documented in the module README. */
    @GetMapping("/{accountId}/candles/{symbol}")
    public ResponseEntity<CandlesResponse> getCandles(
        @PathVariable("accountId") @Min(1) Long accountId,
        @PathVariable("symbol") @Size(min = 1, max = 20) String symbol,
        @RequestParam(value = "range", defaultValue = "3M") @Pattern(regexp = "1M|3M|6M|1Y") String range,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        CandleRange candleRange = CandleRange.fromCode(range).orElseThrow();
        return ResponseEntity.ok(candleService.getCandles(authenticatedAccountId, accountId, symbol, candleRange));
    }
}
