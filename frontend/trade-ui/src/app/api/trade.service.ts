import { HttpClient } from '@angular/common/http';
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

  deposit(accountId: number, body: CashMovementRequest): Observable<CashTransactionResponse> {
    return deposit(this.http, this.config.rootUrl, { id: accountId, body }).pipe(map((r) => r.body));
  }

  withdraw(accountId: number, body: CashMovementRequest): Observable<CashTransactionResponse> {
    return withdraw(this.http, this.config.rootUrl, { id: accountId, body }).pipe(map((r) => r.body));
  }

  getCashTransactions(accountId: number, limit?: number): Observable<CashTransactionResponse[]> {
    return getCashTransactions(this.http, this.config.rootUrl, { id: accountId, limit }).pipe(map((r) => r.body));
  }
}
