package com.team5.advice;

import com.team5.auth.AuthenticatedAccountId;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/accounts/{id}/advisor")
public class TradeAdviceController {

    private final TradeAdviceService tradeAdviceService;

    public TradeAdviceController(TradeAdviceService tradeAdviceService) {
        this.tradeAdviceService = tradeAdviceService;
    }

    @GetMapping("/signals")
    public ResponseEntity<TradeAdviceResponse> getSignals(
        @PathVariable("id") @Min(1) Long accountId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(tradeAdviceService.getSignals(authenticatedAccountId, accountId));
    }
}