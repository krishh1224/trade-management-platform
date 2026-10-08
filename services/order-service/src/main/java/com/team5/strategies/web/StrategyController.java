package com.team5.strategies.web;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.strategies.dto.CreateStrategyRequest;
import com.team5.strategies.dto.StrategyChartResponse;
import com.team5.strategies.dto.StrategyExecutionResponse;
import com.team5.strategies.dto.StrategyListResponse;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.service.StrategyChartService;
import com.team5.strategies.service.StrategyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// contracts/strategies-api.yaml. Customer tokens only: a strategy's own delegated token is refused
// here by SecurityConfig, so a strategy can never re-enable itself or change its limits. There is no
// route that runs a strategy or places its order: orders come only from market-data.
@RestController
@Validated
@RequestMapping("/api/v1/accounts/{id}/strategies")
public class StrategyController {

    private final StrategyService strategyService;
    private final StrategyChartService chartService;

    public StrategyController(StrategyService strategyService, StrategyChartService chartService) {
        this.strategyService = strategyService;
        this.chartService = chartService;
    }

    @GetMapping
    public ResponseEntity<StrategyListResponse> listStrategies(
        @PathVariable("id") @Min(1) long accountId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(strategyService.list(authenticatedAccountId, accountId));
    }

    @PostMapping
    public ResponseEntity<StrategyResponse> createStrategy(
        @PathVariable("id") @Min(1) long accountId,
        @Valid @RequestBody CreateStrategyRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(strategyService.create(authenticatedAccountId, accountId, request));
    }

    @GetMapping("/{strategyId}")
    public ResponseEntity<StrategyResponse> getStrategy(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(strategyService.get(authenticatedAccountId, accountId, strategyId));
    }

    @PostMapping("/{strategyId}/disable")
    public ResponseEntity<StrategyResponse> disableStrategy(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(strategyService.disable(authenticatedAccountId, accountId, strategyId));
    }

    @PostMapping("/{strategyId}/enable")
    public ResponseEntity<StrategyResponse> enableStrategy(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(strategyService.enable(authenticatedAccountId, accountId, strategyId));
    }

    @GetMapping("/{strategyId}/executions")
    public ResponseEntity<List<StrategyExecutionResponse>> listExecutions(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(strategyService.executions(authenticatedAccountId, accountId, strategyId));
    }

    @GetMapping("/{strategyId}/chart")
    public ResponseEntity<StrategyChartResponse> getChart(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(chartService.chart(authenticatedAccountId, accountId, strategyId));
    }

    @DeleteMapping("/{strategyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStrategy(
        @PathVariable("id") @Min(1) long accountId,
        @PathVariable("strategyId") @Min(1) long strategyId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        strategyService.delete(authenticatedAccountId, accountId, strategyId);
    }
}
