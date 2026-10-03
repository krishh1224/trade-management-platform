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
import {
  AccountResponse,
  BalanceResponse,
  OrderHistoryEntry,
  OrderResponse,
  PlaceOrderRequest,
  PositionResponse
} from './generated/trade-api/models';

export interface InstrumentOption {
  symbol: string;
  name: string;
  currency: string;
}

export interface ClientProfile {
  accountId: number;
  name: string;
  email: string;
  phone: string;
  dob: string;
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

  getOrders(accountId: number, filter: Omit<GetOrders$Params, 'id'> = {}): Observable<OrderHistoryEntry[]> {
    return getOrders(this.http, this.config.rootUrl, { id: accountId, ...filter }).pipe(map((r) => r.body));
  }

  getInstruments(accountId: number, prefix = ''): Observable<InstrumentOption[]> {
    const params = new HttpParams({ fromObject: prefix ? { prefix } : {} });
    return this.http.get<InstrumentOption[]>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/instruments`, { params });
  }

  getClientProfile(accountId: number): Observable<ClientProfile> {
    return this.http.get<ClientProfile>(`${this.config.rootUrl}/api/v1/accounts/${accountId}/profile`);
  }
}
