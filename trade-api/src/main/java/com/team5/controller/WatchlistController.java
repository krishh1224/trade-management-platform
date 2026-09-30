package com.team5.controller;

import com.team5.auth.AuthenticatedAccountId;
import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.response.WatchlistItemResponse;
import com.team5.service.WatchlistService;
import jakarta.validation.Valid;
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

@RestController
@Validated
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @PostMapping
    public ResponseEntity<WatchlistItemResponse> addToWatchlist(
        @Valid @RequestBody AddToWatchlistRequest request,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(watchlistService.addToWatchlist(authenticatedAccountId, request));
    }

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> listWatchlist(
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        return ResponseEntity.ok(watchlistService.listWatchlist(authenticatedAccountId));
    }

    @DeleteMapping("/{instrumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromWatchlist(
        @PathVariable long instrumentId,
        @AuthenticatedAccountId long authenticatedAccountId
    ) {
        watchlistService.removeFromWatchlist(authenticatedAccountId, instrumentId);
    }
}
