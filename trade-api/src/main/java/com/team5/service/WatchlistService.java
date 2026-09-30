package com.team5.service;

import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.response.WatchlistItemResponse;
import java.util.List;

public interface WatchlistService {

    WatchlistItemResponse addToWatchlist(long authenticatedAccountId, AddToWatchlistRequest request);

    List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId);

    void removeFromWatchlist(long authenticatedAccountId, long instrumentId);
}
