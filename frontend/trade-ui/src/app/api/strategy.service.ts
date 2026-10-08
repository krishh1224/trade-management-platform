import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiConfiguration } from './generated/trade-api/api-configuration';

// contracts/strategies-api.yaml

export type StrategyType = 'MA_CROSSOVER' | 'BOLLINGER_BANDS';
export type StrategyStatus = 'ENABLED' | 'DISABLED' | 'HALTED';
export type ExecutionOutcome =
  | 'PLACED'
  | 'FAILED'
  | 'SKIPPED_DISABLED'
  | 'SKIPPED_SPEND_LIMIT'
  | 'SKIPPED_POSITION_LIMIT'
  | 'SKIPPED_NO_HOLDING';

export interface Strategy {
  strategyId: number;
  accountId: number;
  name: string;
  symbol: string;
  type: StrategyType;
  shortWindow: number | null;
  longWindow: number | null;
  bandWindow: number | null;
  bandWidth: number | null;
  /** Shares per order. */
  quantity: number;
  maxSpend: number;
  /** BUY orders placed so far, at their limit price. */
  spent: number;
  remainingSpend: number;
  maxPosition: number;
  maxConsecutiveFailures: number;
  consecutiveFailures: number;
  ordersPlaced: number;
  status: StrategyStatus;
  /** Why it is DISABLED or HALTED. */
  statusReason: string | null;
  lastTriggeredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StrategyList {
  maxStrategies: number;
  strategies: Strategy[];
}

export interface CreateStrategyRequest {
  name: string;
  symbol: string;
  type: StrategyType;
  shortWindow?: number;
  longWindow?: number;
  bandWindow?: number;
  bandWidth?: number;
  quantity: number;
  maxSpend: number;
  maxPosition: number;
  maxConsecutiveFailures?: number;
}

/** One time a strategy's rule fired, and what was done. Only PLACED means an order exists. */
export interface StrategyExecution {
  executionId: number;
  quoteEventId: string;
  signal: 'BUY' | 'SELL';
  outcome: ExecutionOutcome;
  quotePrice: number;
  orderPrice: number | null;
  quantity: number | null;
  orderId: string | null;
  detail: string | null;
  createdAt: string;
}

/** One stored price, with the lines the rule compares at it (null until the window fits). */
export interface StrategyChartPoint {
  quotedAt: string;
  price: number;
  shortAverage: number | null;
  longAverage: number | null;
  middleBand: number | null;
  upperBand: number | null;
  lowerBand: number | null;
}

/** A time the rule fired, on the point of the quote it fired on. */
export interface StrategyChartMarker {
  pointIndex: number;
  quotedAt: string;
  quotePrice: number;
  signal: 'BUY' | 'SELL';
  outcome: ExecutionOutcome;
  orderId: string | null;
  detail: string | null;
}

export interface StrategyChart {
  strategyId: number;
  symbol: string;
  type: StrategyType;
  /** Prices the rule needs before it can fire. */
  requiredPrices: number;
  /** Oldest first. */
  points: StrategyChartPoint[];
  markers: StrategyChartMarker[];
}

@Injectable({ providedIn: 'root' })
export class StrategyService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfiguration);

  list(accountId: number): Observable<StrategyList> {
    return this.http.get<StrategyList>(this.base(accountId));
  }

  create(accountId: number, body: CreateStrategyRequest): Observable<Strategy> {
    return this.http.post<Strategy>(this.base(accountId), body);
  }

  /** Effective before the response arrives: no order from the strategy starts after it. */
  disable(accountId: number, strategyId: number): Observable<Strategy> {
    return this.http.post<Strategy>(`${this.base(accountId)}/${strategyId}/disable`, null);
  }

  /** Also clears the failure run of a HALTED strategy. */
  enable(accountId: number, strategyId: number): Observable<Strategy> {
    return this.http.post<Strategy>(`${this.base(accountId)}/${strategyId}/enable`, null);
  }

  delete(accountId: number, strategyId: number): Observable<void> {
    return this.http.delete<void>(`${this.base(accountId)}/${strategyId}`);
  }

  executions(accountId: number, strategyId: number): Observable<StrategyExecution[]> {
    return this.http.get<StrategyExecution[]>(`${this.base(accountId)}/${strategyId}/executions`);
  }

  chart(accountId: number, strategyId: number): Observable<StrategyChart> {
    return this.http.get<StrategyChart>(`${this.base(accountId)}/${strategyId}/chart`);
  }

  private base(accountId: number): string {
    return `${this.config.rootUrl}/api/v1/accounts/${accountId}/strategies`;
  }
}
