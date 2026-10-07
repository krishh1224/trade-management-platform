import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiConfiguration } from './generated/trade-api/api-configuration';
import { cancelOrder } from './generated/trade-api/fn/orders/cancel-order';
import { placeOrder } from './generated/trade-api/fn/orders/place-order';
import { getAccount } from './generated/trade-api/fn/accounts/get-account';
import { getBalance } from './generated/trade-api/fn/accounts/get-balance';
import { getOrders, GetOrders$Params } from './generated/trade-api/fn/accounts/get-orders';
import { getPositions } from './generated/trade-api/fn/accounts/get-positions';
import { deposit } from './generated/trade-api/fn/funds/deposit';
import { getCashTransactions } from './generated/trade-api/fn/funds/get-cash-transactions';
import { withdraw } from './generated/trade-api/fn/funds/withdraw';
import {
  AccountResponse,
  BalanceResponse,
  CashMovementRequest,
  CashTransactionResponse,
  OrderHistoryEntry,
  OrderResponse,
  PlaceOrderRequest,
  PositionResponse
} from './generated/trade-api/models';

export interface WatchlistItem {
  watchlistId: string;
  watchlistGroupId: string;
  watchlistName: string;
  instrumentId: number;
  symbol: string;
  instrumentName: string;
  addedAt: string;
  lastPrice: number | null;
  changePct: number | null;
}

export interface AddToWatchlistRequest {
  accountId: number;
  symbol: string;
  watchlistGroupId?: string;
}

export interface WatchlistGroup {
  watchlistGroupId: string;
  name: string;
  createdAt: string;
}

export interface CreateWatchlistGroupRequest {
  name: string;
}

export interface InstrumentOption {
  symbol: string;
  name: string;
  currency: string;
}

/**
 * Order history row as trade-api returns it. `settlementAmount` is the cash actually moved on a
 * FILLED order, in the account's currency. The server sends it but trade-api.yaml does not declare
 * it, so it is added here rather than in the generated model (which `check:api` regenerates).
 */
export type OrderHistoryRow = OrderHistoryEntry & { settlementAmount?: number | null };

export interface ClientProfile {
  accountId: number;
  name: string;
  email: string;
  phone: string;
  dob: string;
}

export type PreferenceCurrency = 'INR' | 'USD';
export type PreferenceTheme = 'LIGHT' | 'DARK';
export type PreferenceAlertChannel = 'EMAIL' | 'PUSH' | 'BROWSER' | 'SMS' | 'NONE';

export interface CustomerPreferences {
  currency: PreferenceCurrency;
  theme: PreferenceTheme;
  alertChannel: PreferenceAlertChannel;
}

// contracts/notifications-api.yaml
export type NotificationType = 'ORDER_FILLED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PRICE_ALERT';
export type NotificationStatus = 'QUEUED' | 'HELD' | 'SENT' | 'FAILED' | 'SKIPPED';

export interface CustomerNotification {
  notificationId: number;
  type: NotificationType;
  /** orders.order_id can exceed Number.MAX_SAFE_INTEGER, so never display or compare it. */
  orderId: number | null;
  title: string;
  message: string;
  status: NotificationStatus;
  /** The channel resolved from preferences at send time; null while QUEUED or HELD. */
  channel: PreferenceAlertChannel | null;
  createdAt: string;
  sentAt: string | null;
  readAt: string | null;
}

/** VAPID public key the browser subscribes with; publicKey is null when push is off on the server. */
export interface PushConfig {
  enabled: boolean;
  publicKey: string | null;
}

/** Hand-written facade over the generated Trade REST API client. Unwraps the HTTP envelope. */
@Injectable({ providedIn: 'root' })
export class TradeService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfiguration);

  placeOrder(body: PlaceOrderRequest): Observable<OrderResponse> {
    return placeOrder(this.http, this.config.rootUrl, { body }).pipe(map((r) => r.body));
  }

  /** @param orderId the order UUID without the `ORD-` display prefix */
  cancelOrder(orderId: string): Observable<OrderResponse> {
    return cancelOrder(this.http, this.config.rootUrl, { id: orderId }).pipe(map((r) => r.body));
  }

  getAccount(accountId: number): Observable<AccountResponse> {
    return getAccount(this.http, this.config.rootUrl, { id: accountId }).pipe(map((r) => r.body));
  }

  getBalance(accountId: number): Observable<BalanceResponse> {
    return getBalance(this.http, this.config.rootUrl, { id: accountId }).pipe(map((r) => r.body));
  }

  getPositions(accountId: number): Observable<PositionResponse[]> {
    return getPositions(this.http, this.config.rootUrl, { id: accountId }).pipe(map((r) => r.body));
  }

  getOrders(accountId: number, filter: Omit<GetOrders$Params, 'id'> = {}): Observable<OrderHistoryRow[]> {
    return getOrders(this.http, this.config.rootUrl, { id: accountId, ...filter }).pipe(map((r) => r.body));
  }

  deposit(accountId: number, body: CashMovementRequest): Observable<CashTransactionResponse> {
    return deposit(this.http, this.config.rootUrl, { id: accountId, body }).pipe(map((r) => r.body));
  }

  withdraw(accountId: number, body: CashMovementRequest): Observable<CashTransactionResponse> {
    return withdraw(this.http, this.config.rootUrl, { id: accountId, body }).pipe(map((r) => r.body));
  }

  getCashTransactions(accountId: number, limit?: number): Observable<CashTransactionResponse[]> {
    return getCashTransactions(this.http, this.config.rootUrl, { id: accountId, limit }).pipe(map((r) => r.body));
  }
 
  getInstruments(accountId: number, prefix = ''): Observable<InstrumentOption[]> {
    const params = new HttpParams({ fromObject: prefix ? { prefix } : {} });
    return this.http.get<InstrumentOption[]>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/instruments`, { params });
  }

  /** Get watchlist items for authenticated user */
  getWatchlist(accountId: number, watchlistGroupId?: string): Observable<WatchlistItem[]> {
    const params = watchlistGroupId ? new HttpParams({ fromObject: { groupId: String(watchlistGroupId) } }) : undefined;
    return this.http.get<WatchlistItem[]>(`${this.config.rootUrl}/api/v1/watchlists`, { params });
  }

  /** Add instrument to watchlist */
  addToWatchlist(request: AddToWatchlistRequest): Observable<WatchlistItem> {
    return this.http.post<WatchlistItem>(`${this.config.rootUrl}/api/v1/watchlists`, request);
  }

  /** Remove watchlist row by watchlist id */
  removeFromWatchlist(watchlistId: string): Observable<void> {
    return this.http.delete<void>(`${this.config.rootUrl}/api/v1/watchlists/${watchlistId}`);
  }

  listWatchlistGroups(): Observable<WatchlistGroup[]> {
    return this.http.get<WatchlistGroup[]>(`${this.config.rootUrl}/api/v1/watchlists/groups`);
  }

  createWatchlistGroup(request: CreateWatchlistGroupRequest): Observable<WatchlistGroup> {
    return this.http.post<WatchlistGroup>(`${this.config.rootUrl}/api/v1/watchlists/groups`, request);
  }

  getClientProfile(accountId: number): Observable<ClientProfile> {
    return this.http.get<ClientProfile>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/profile`);
  }

  getPreferences(accountId: number): Observable<CustomerPreferences> {
    return this.http.get<CustomerPreferences>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/preferences`);
  }

  updatePreferences(accountId: number, body: CustomerPreferences): Observable<CustomerPreferences> {
    return this.http.put<CustomerPreferences>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/preferences`, body);
  }

  getNotifications(accountId: number, status?: NotificationStatus, limit = 50): Observable<CustomerNotification[]> {
    let params = new HttpParams().set('limit', limit);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<CustomerNotification[]>(
      `${this.config.rootUrl}/api/v1/accounts/${accountId}/notifications`,
      { params }
    );
  }

  getPushConfig(accountId: number): Observable<PushConfig> {
    return this.http.get<PushConfig>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/push-subscription/config`);
  }

  /** @param subscription the browser's PushSubscription.toJSON(), sent unchanged */
  savePushSubscription(accountId: number, subscription: PushSubscriptionJSON): Observable<void> {
    return this.http.put<void>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/push-subscription`, subscription);
  }

  deletePushSubscription(accountId: number, endpoint: string): Observable<void> {
    return this.http.delete<void>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/push-subscription`, {
      params: new HttpParams().set('endpoint', endpoint)
    });
  }

  markNotificationRead(accountId: number, notificationId: number): Observable<CustomerNotification> {
    return this.http.put<CustomerNotification>(
      `${this.config.rootUrl}/api/v1/accounts/${accountId}/notifications/${notificationId}/read`,
      null
    );
  }
}
