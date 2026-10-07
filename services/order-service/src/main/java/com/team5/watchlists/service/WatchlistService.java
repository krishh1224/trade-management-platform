package com.team5.watchlists.service;

import com.team5.watchlists.dto.AddToWatchlistRequest;
import com.team5.watchlists.dto.CreateWatchlistGroupRequest;
import com.team5.watchlists.dto.WatchlistGroupResponse;
import com.team5.watchlists.dto.WatchlistItemResponse;
import java.util.List;

public interface WatchlistService {

    WatchlistItemResponse addToWatchlist(long authenticatedAccountId, AddToWatchlistRequest request);

    List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId);

    List<WatchlistItemResponse> listWatchlist(long authenticatedAccountId, Long watchlistGroupId);

    List<WatchlistGroupResponse> listWatchlistGroups(long authenticatedAccountId);

    WatchlistGroupResponse createWatchlistGroup(long authenticatedAccountId, CreateWatchlistGroupRequest request);

    void removeFromWatchlist(long authenticatedAccountId, long watchlistId);

    // a custom watchlist, with its entries and their price alerts; never the Favourite one
    void deleteWatchlistGroup(long authenticatedAccountId, long watchlistGroupId);
}
