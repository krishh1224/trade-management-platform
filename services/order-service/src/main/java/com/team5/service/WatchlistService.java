package com.team5.service;

import com.team5.dto.request.AddToWatchlistRequest;
import com.team5.dto.request.CreateWatchlistGroupRequest;
import com.team5.dto.response.WatchlistGroupResponse;
import com.team5.dto.response.WatchlistItemResponse;
import java.util.List;

public interface WatchlistService {

    WatchlistItemResponse addToWatchlist(long authenticatedAccountId, AddToWatchlistRequest request);

    List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId);

    List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId, Long watchlistGroupId);

    List<WatchlistGroupResponse> listWatchlistGroups(long authenticatedAccountId);

    WatchlistGroupResponse createWatchlistGroup(long authenticatedAccountId, CreateWatchlistGroupRequest request);

    void removeFromWatchlist(long authenticatedAccountId, long watchlistId);
}
