import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import {
  CANDLE_RANGES,
  CandleRange,
  CandlesResponse,
  PortfolioService,
  PortfolioSummary,
  PricedPosition,
  isPricingUnavailable
} from '../api/portfolio.service';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { FundsDialogComponent } from '../funds/funds-dialog.component';
import { currencyForSymbol } from '../instruments/instrument-currency';
import { FooterComponent } from '../shared/footer.component';
import { ThemeService } from '../theme/theme.service';
import { CandleChartComponent } from './candle-chart.component';

/** `live`: everything priced and fresh. `delayed`: priced, but a quote is stale. `partial`: some holdings unpriced. */
export type PortfolioState = 'live' | 'delayed' | 'partial' | 'unavailable';

const STATE_LABELS: Record<PortfolioState, string> = {
  live: 'Live',
  delayed: 'Delayed quotes',
  partial: 'Partly priced',
  unavailable: 'Prices unavailable'
};

const MASK = '••••••••';

export type CandleLoad =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: CandlesResponse };

@Component({
  selector: 'app-portfolio',
  imports: [RouterLink, DecimalPipe, DatePipe, FooterComponent, FundsDialogComponent, CandleChartComponent],
  templateUrl: './portfolio.component.html',
  styleUrl: './portfolio.component.css'
})
export class PortfolioComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly portfolioService = inject(PortfolioService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly fundsOpen = signal(false);
  readonly profileMenuOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;

  readonly summary = signal<PortfolioSummary | null>(null);
  readonly rows = signal<PricedPosition[]>([]);
  readonly cash = signal<number | null>(null);
  readonly baseCurrency = signal('INR');
  readonly loading = signal(true);
  readonly error = signal('');
  /** True when the server answered MKT-503: holdings are shown at cost, with no prices at all. */
  readonly pricingDown = signal(false);
  readonly valuesHidden = signal(false);

  /** The holding whose price history is open, and the range being shown. */
  readonly expandedSymbol = signal<string | null>(null);
  readonly candleRange = signal<CandleRange>('3M');
  readonly candleLoad = signal<CandleLoad | null>(null);
  readonly candleRanges = CANDLE_RANGES;
  private readonly candleCache = new Map<string, CandlesResponse>();

  readonly unpricedCount = computed(() => this.rows().filter((row) => row.lastPrice === null).length);

  readonly state = computed<PortfolioState>(() => {
    if (this.pricingDown()) {
      return 'unavailable';
    }
    if (this.summary()?.partial || this.unpricedCount() > 0) {
      return 'partial';
    }
    return this.rows().some((row) => row.stale) ? 'delayed' : 'live';
  });

  readonly stateLabel = computed(() => STATE_LABELS[this.state()]);

  /** Change over the visible range, first close to last close, plus its high and low. */
  readonly candleStats = computed(() => {
    const load = this.candleLoad();
    if (load?.status !== 'ready' || load.data.candles.length === 0) {
      return null;
    }
    const bars = load.data.candles;
    const first = bars[0].close;
    const last = bars[bars.length - 1].close;
    return {
      last,
      changePercent: ((last - first) / first) * 100,
      high: Math.max(...bars.map((bar) => bar.high)),
      low: Math.min(...bars.map((bar) => bar.low))
    };
  });

  constructor() {
    this.themeService.initialize();
    this.load();
  }

  refresh(): void {
    if (!this.loading()) {
      this.load();
    }
  }

  toggleRow(symbol: string): void {
    if (this.expandedSymbol() === symbol) {
      this.expandedSymbol.set(null);
      return;
    }
    this.expandedSymbol.set(symbol);
    this.loadCandles(symbol, this.candleRange());
  }

  selectRange(range: CandleRange): void {
    const symbol = this.expandedSymbol();
    this.candleRange.set(range);
    if (symbol !== null) {
      this.loadCandles(symbol, range);
    }
  }

  retryCandles(): void {
    const symbol = this.expandedSymbol();
    if (symbol !== null) {
      this.loadCandles(symbol, this.candleRange());
    }
  }

  toggleValues(): void {
    this.valuesHidden.update((hidden) => !hidden);
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  closeProfileMenu(): void {
    this.profileMenuOpen.set(false);
  }

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  /** Formats an amount in the base currency; `signed` adds a leading + to gains. */
  money(value: number | null | undefined, signed = false): string {
    if (this.valuesHidden()) {
      return MASK;
    }
    if (value === null || value === undefined) {
      return '—';
    }
    const formatted = value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${this.baseCurrency()} ${signed && value > 0 ? '+' : ''}${formatted}`;
  }

  percent(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
  }

  pnlTone(value: number | null | undefined): 'up' | 'down' | 'flat' {
    if (value === null || value === undefined || value === 0) {
      return 'flat';
    }
    return value > 0 ? 'up' : 'down';
  }

  /** Time of day for today's observations, with the date once a quote is older than that (a closed market). */
  timeLabel(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const observed = new Date(iso);
    if (Number.isNaN(observed.getTime())) {
      return '—';
    }
    const time = observed.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    if (observed.toDateString() === new Date().toDateString()) {
      return time;
    }
    return `${observed.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${time}`;
  }

  // The server fetches a year of bars once per symbol and slices it, so changing the range costs no quota.
  private loadCandles(symbol: string, range: CandleRange): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      return;
    }
    const cached = this.candleCache.get(`${symbol}|${range}`);
    if (cached) {
      this.candleLoad.set({ status: 'ready', data: cached });
      return;
    }

    this.candleLoad.set({ status: 'loading' });
    this.portfolioService.getCandles(accountId, symbol, range).subscribe({
      next: (data) => {
        // Ignore an answer for a row the user has since closed or switched away from.
        if (this.expandedSymbol() !== symbol || this.candleRange() !== range) {
          return;
        }
        if (!data.stale) {
          this.candleCache.set(`${symbol}|${range}`, data);
        }
        this.candleLoad.set({ status: 'ready', data });
      },
      error: (error: unknown) => {
        if (this.expandedSymbol() !== symbol || this.candleRange() !== range) {
          return;
        }
        this.candleLoad.set({
          status: 'error',
          message: isPricingUnavailable(error)
            ? 'Price history is unavailable right now. Try again in a moment.'
            : toUserFacingErrorMessage(error, { fallbackMessage: 'Price history could not be loaded. Try again.' })
        });
      }
    });
  }

  private load(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.pricingDown.set(false);

    // Sequential on purpose: the summary warms the server's quote cache, so the positions call that
    // follows costs no extra market-data quota.
    this.portfolioService
      .getSummary(accountId)
      .pipe(
        switchMap((summary) =>
          this.portfolioService.getPositions(accountId).pipe(map((positions) => ({ summary, positions })))
        )
      )
      .subscribe({
        next: ({ summary, positions }) => {
          this.summary.set(summary);
          this.rows.set(positions);
          this.cash.set(summary.cashBalance);
          this.baseCurrency.set(summary.baseCurrency);
          this.loading.set(false);
        },
        error: (error: unknown) => this.handleFailure(error, accountId)
      });
  }

  private handleFailure(error: unknown, accountId: number): void {
    this.summary.set(null);
    this.rows.set([]);

    if (!isPricingUnavailable(error)) {
      this.error.set(toUserFacingErrorMessage(error, { fallbackMessage: 'Your portfolio could not be loaded. Please try again.' }));
      this.loading.set(false);
      return;
    }

    // The documented degraded mode: show what we hold, at cost, rather than an empty portfolio.
    this.pricingDown.set(true);
    forkJoin({
      positions: this.tradeService.getPositions(accountId),
      balance: this.tradeService.getBalance(accountId).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ positions, balance }) => {
        this.rows.set(
          positions.map((position) => ({
            accountId: position.accountId,
            symbol: position.symbol,
            quantity: position.quantity,
            averageCost: position.averageCost,
            costBasis: Math.round(position.quantity * position.averageCost * 100) / 100,
            lastPrice: null,
            marketValue: null,
            unrealisedPnl: null,
            unrealisedPnlPercent: null,
            currency: currencyForSymbol(position.symbol),
            priceAsOf: null,
            stale: true
          }))
        );
        this.cash.set(balance?.cashBalance ?? null);
        this.baseCurrency.set(balance?.currency ?? 'INR');
        this.loading.set(false);
      },
      error: (fallbackError: unknown) => {
        this.pricingDown.set(false);
        this.error.set(toUserFacingErrorMessage(fallbackError, { fallbackMessage: 'Your holdings could not be loaded. Please try again.' }));
        this.loading.set(false);
      }
    });
  }
}
