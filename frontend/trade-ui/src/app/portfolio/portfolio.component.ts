import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import {
  CANDLE_RANGES,
  CandleRange,
  CandlesResponse,
  PnlResponse,
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

/** How far back realised profit and loss is counted. Unrealised is always as at now. */
export type PnlPeriod = 'ALL' | '30D' | '90D' | '1Y';

export const PNL_PERIODS: readonly { code: PnlPeriod; label: string; days: number | null }[] = [
  { code: 'ALL', label: 'All time', days: null },
  { code: '30D', label: '30D', days: 30 },
  { code: '90D', label: '90D', days: 90 },
  { code: '1Y', label: '1Y', days: 365 }
];

export type PnlLoad =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: PnlResponse };

export type CandleLoad =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: CandlesResponse };

interface AllocationSlice {
  symbol: string;
  value: number;
  share: number;
  offset: number;
  color: string;
}

interface PnlContributionRow {
  symbol: string;
  totalPnl: number;
  width: number;
  tone: 'up' | 'down' | 'flat';
}

const CHART_COLORS = ['#2f6df6', '#16c784', '#f5a524', '#f05252', '#8b5cf6', '#0ea5e9'];

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

  /** Profit and loss by instrument. It loads after the holdings and fails on its own without breaking the page. */
  readonly pnlPeriod = signal<PnlPeriod>('ALL');
  readonly pnlLoad = signal<PnlLoad | null>(null);
  readonly pnlPeriods = PNL_PERIODS;

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

  readonly allocationSlices = computed<AllocationSlice[]>(() => {
    const priced = this.rows()
      .filter((row) => row.marketValue !== null && row.marketValue > 0)
      .sort((a, b) => (b.marketValue ?? 0) - (a.marketValue ?? 0));
    if (priced.length === 0) {
      return [];
    }

    const total = priced.reduce((sum, row) => sum + (row.marketValue ?? 0), 0);
    if (total <= 0) {
      return [];
    }

    const top = priced.slice(0, 5).map((row) => ({ symbol: row.symbol, value: row.marketValue ?? 0 }));
    const remainder = priced.slice(5).reduce((sum, row) => sum + (row.marketValue ?? 0), 0);
    if (remainder > 0) {
      top.push({ symbol: 'Other', value: remainder });
    }

    let offset = 0;
    return top.map((entry, index) => {
      const share = entry.value / total;
      const slice: AllocationSlice = {
        symbol: entry.symbol,
        value: entry.value,
        share,
        offset,
        color: CHART_COLORS[index % CHART_COLORS.length]
      };
      offset -= share * 100;
      return slice;
    });
  });

  readonly largestAllocation = computed(() => this.allocationSlices()[0]?.symbol ?? '—');

  readonly pnlContributionRows = computed<PnlContributionRow[]>(() => {
    const load = this.pnlLoad();
    if (!load || load.status !== 'ready' || !load.data.bySymbol || load.data.bySymbol.length === 0) {
      return [];
    }

    const sorted = [...load.data.bySymbol]
      .filter((row) => row.totalPnl !== 0)
      .sort((a, b) => Math.abs(b.totalPnl) - Math.abs(a.totalPnl))
      .slice(0, 6);

    if (sorted.length === 0) {
      return [];
    }

    const maxAbs = Math.max(...sorted.map((row) => Math.abs(row.totalPnl)));
    return sorted.map((row) => ({
      symbol: row.symbol,
      totalPnl: row.totalPnl,
      width: Math.max(10, Math.round((Math.abs(row.totalPnl) / maxAbs) * 100)),
      tone: this.pnlTone(row.totalPnl)
    }));
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

  selectPnlPeriod(period: PnlPeriod): void {
    const accountId = this.claims()?.accountId ?? null;
    this.pnlPeriod.set(period);
    if (accountId !== null && !this.pricingDown()) {
      this.loadPnl(accountId);
    }
  }

  /** A profit or loss amount without the currency, signed, or masked when amounts are hidden. */
  amount(value: number | null | undefined): string {
    if (this.valuesHidden()) {
      return MASK;
    }
    if (value === null || value === undefined) {
      return '—';
    }
    const formatted = value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return value > 0 ? `+${formatted}` : formatted;
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

  ratio(value: number): string {
    return `${(value * 100).toFixed(1)}%`;
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

  // Only the realised figure follows the period; unrealised is always as at now.
  private loadPnl(accountId: number): void {
    const period = this.pnlPeriod();
    const days = PNL_PERIODS.find((option) => option.code === period)?.days ?? null;
    this.pnlLoad.set({ status: 'loading' });

    this.portfolioService.getPnl(accountId, { from: days === null ? undefined : daysAgo(days), bySymbol: true }).subscribe({
      next: (data) => {
        if (this.pnlPeriod() === period) {
          this.pnlLoad.set({ status: 'ready', data });
        }
      },
      error: (error: unknown) => {
        if (this.pnlPeriod() === period) {
          this.pnlLoad.set({
            status: 'error',
            message: isPricingUnavailable(error)
              ? 'Profit and loss needs live prices, which are unavailable right now.'
              : toUserFacingErrorMessage(error, { fallbackMessage: 'Profit and loss could not be loaded. Try again.' })
          });
        }
      }
    });
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
          this.loadPnl(accountId);
        },
        error: (error: unknown) => this.handleFailure(error, accountId)
      });
  }

  private handleFailure(error: unknown, accountId: number): void {
    this.summary.set(null);
    this.rows.set([]);
    this.pnlLoad.set(null);

    if (!isPricingUnavailable(error)) {
      this.error.set(toUserFacingErrorMessage(error, { fallbackMessage: 'Your portfolio could not be loaded. Please try again.' }));
      this.loading.set(false);
      return;
    }

    // The documented degraded mode: show what we hold, at cost, rather than an empty portfolio.
    this.pricingDown.set(true);
    this.pnlLoad.set({ status: 'error', message: 'Profit and loss needs live prices, which are unavailable right now.' });
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

/** Local calendar date `days` ago as yyyy-MM-dd, the format the P&L route takes. */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
