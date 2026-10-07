import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiConfiguration } from './generated/trade-api/api-configuration';

/** Totals for one account, as `GET /api/v1/portfolio/{accountId}` returns them (contracts/portfolio-api.yaml). */
export interface PortfolioSummary {
  accountId: number;
  baseCurrency: string;
  cashBalance: number;
  marketValue: number;
  costBasis: number;
  unrealisedPnl: number;
  unrealisedPnlPercent: number | null;
  realisedPnl: number;
  totalValue: number;
  positionCount: number;
  /** True when at least one holding could not be priced, so the totals leave it out. */
  partial: boolean;
  asOf: string;
}

/** One open holding. Price fields are null when the instrument could not be priced. */
export interface PricedPosition {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  costBasis: number;
  lastPrice: number | null;
  marketValue: number | null;
  unrealisedPnl: number | null;
  unrealisedPnlPercent: number | null;
  currency: string;
  /** When the quote was observed upstream, not when we fetched it. */
  priceAsOf: string | null;
  stale: boolean;
}

export type CandleRange = '1M' | '3M' | '6M' | '1Y';
export const CANDLE_RANGES: readonly CandleRange[] = ['1M', '3M', '6M', '1Y'];

/** One daily bar. `synthetic` marks a bar the provider estimated rather than observed. */
export interface Candle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  synthetic: boolean;
}

/** Daily candles for a held instrument. `stale` means the provider was unreachable and a saved copy was served. */
export interface CandlesResponse {
  symbol: string;
  currency: string;
  interval: string;
  range: CandleRange;
  stale: boolean;
  asOf: string;
  candles: Candle[];
}

/** Hand-written client for the Portfolio module. The browser never calls Fauxnance; prices arrive here. */
@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfiguration);

  getSummary(accountId: number): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${this.config.rootUrl}/api/v1/portfolio/${accountId}`);
  }

  getPositions(accountId: number, symbol?: string): Observable<PricedPosition[]> {
    const params = new HttpParams({ fromObject: symbol ? { symbol } : {} });
    return this.http.get<PricedPosition[]>(`${this.config.rootUrl}/api/v1/portfolio/${accountId}/positions`, {
      params
    });
  }

  getCandles(accountId: number, symbol: string, range: CandleRange = '3M'): Observable<CandlesResponse> {
    return this.http.get<CandlesResponse>(
      `${this.config.rootUrl}/api/v1/portfolio/${accountId}/candles/${encodeURIComponent(symbol)}`,
      { params: new HttpParams({ fromObject: { range } }) }
    );
  }
}

/** True for the documented "no held instrument could be priced" answer (503, MKT-503). */
export function isPricingUnavailable(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 503) {
    return false;
  }
  const body: unknown = error.error;
  return typeof body === 'object' && body !== null && (body as { errorCode?: unknown }).errorCode === 'MKT-503';
}
