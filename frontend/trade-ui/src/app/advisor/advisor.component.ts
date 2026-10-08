import { DestroyRef, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AdviceSignalRow, SkippedAdviceRow, TradeService } from '../api/trade.service';
import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { NotificationBellComponent } from '../notifications/notification-bell.component';

@Component({
  selector: 'app-advisor',
  imports: [RouterLink, NotificationBellComponent],
  templateUrl: './advisor.component.html',
  styleUrl: './advisor.component.css'
})
export class AdvisorComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly profileMenuOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly generatedAt = signal<string | null>(null);
  readonly signals = signal<AdviceSignalRow[]>([]);
  readonly skipped = signal<SkippedAdviceRow[]>([]);
  readonly selectedSymbol = signal<string | null>(null);
  readonly selectedOrigin = signal<'HOLDING' | 'WATCHLIST' | null>(null);

  readonly visibleSignals = computed(() => this.signals().filter((signal) => this.matchesFocus(signal.symbol, signal.origin)));
  readonly visibleSkipped = computed(() => this.skipped().filter((signal) => this.matchesFocus(signal.symbol, signal.origin)));
  readonly buySignals = computed(() => this.visibleSignals().filter((signal) => signal.signal === 'BUY'));
  readonly sellSignals = computed(() => this.visibleSignals().filter((signal) => signal.signal === 'SELL'));
  readonly holdSignals = computed(() => this.visibleSignals().filter((signal) => signal.signal === 'HOLD'));
  readonly holdingSignals = computed(() => this.visibleSignals().filter((signal) => signal.origin === 'HOLDING'));
  readonly watchlistSignals = computed(() => this.visibleSignals().filter((signal) => signal.origin === 'WATCHLIST'));
  readonly hasResults = computed(() => this.visibleSignals().length > 0 || this.visibleSkipped().length > 0);
  readonly generatedAtLabel = computed(() => formatDateTime(this.generatedAt()));
  readonly focusTitle = computed(() => {
    const symbol = this.selectedSymbol();
    if (!symbol) {
      return null;
    }
    return this.selectedOrigin() === 'WATCHLIST'
      ? `Showing advisor output for ${symbol} from your watchlist.`
      : `Showing advisor output for ${symbol} from your holdings.`;
  });

  constructor() {
    this.themeService.initialize();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const symbol = params.get('symbol');
      const origin = params.get('origin');
      this.selectedSymbol.set(symbol);
      this.selectedOrigin.set(origin === 'HOLDING' || origin === 'WATCHLIST' ? origin : null);
    });
    this.loadAdvice();
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

  refresh(): void {
    this.loadAdvice();
  }

  clearFocus(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { symbol: null, origin: null },
      queryParamsHandling: 'merge'
    });
  }

  signalClass(signal: AdviceSignalRow): string {
    return signal.signal.toLowerCase();
  }

  originLabel(origin: 'HOLDING' | 'WATCHLIST'): string {
    return origin === 'HOLDING' ? 'Holding' : 'Watchlist';
  }

  metricLabel(signal: AdviceSignalRow): string {
    if (signal.unrealisedPnlPercent !== null) {
      return 'Unrealised P&L';
    }
    if (signal.changePct !== null) {
      return 'Latest move';
    }
    return 'Signal';
  }

  metricValue(signal: AdviceSignalRow): string {
    if (signal.unrealisedPnlPercent !== null) {
      return formatPercent(signal.unrealisedPnlPercent);
    }
    if (signal.changePct !== null) {
      return formatPercent(signal.changePct);
    }
    return signal.signal;
  }

  priceLabel(signal: AdviceSignalRow): string {
    if (signal.lastPrice === null) {
      return 'No price';
    }
    return signal.currency ? formatMoney(signal.lastPrice, signal.currency) : signal.lastPrice.toFixed(2);
  }

  priceAsOfLabel(signal: AdviceSignalRow): string {
    if (!signal.priceAsOf) {
      return signal.stale ? 'Stored price is stale' : 'Stored price';
    }
    return `${signal.stale ? 'Stale price' : 'Price as of'} ${formatDateTime(signal.priceAsOf)}`;
  }

  trackBySymbol(index: number, row: AdviceSignalRow | SkippedAdviceRow): string {
    return `${row.origin}:${row.symbol}:${index}`;
  }

  private matchesFocus(symbol: string, origin: 'HOLDING' | 'WATCHLIST'): boolean {
    const selectedSymbol = this.selectedSymbol();
    const selectedOrigin = this.selectedOrigin();
    if (selectedSymbol !== null && symbol !== selectedSymbol) {
      return false;
    }
    if (selectedOrigin !== null && origin !== selectedOrigin) {
      return false;
    }
    return true;
  }

  private loadAdvice(): void {
    const accountId = this.claims()?.accountId;
    if (!accountId) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.tradeService
      .getAdviceSignals(accountId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.signals.set(response.signals);
          this.skipped.set(response.skipped);
          this.generatedAt.set(response.generatedAt);
        },
        error: (error: unknown) => {
          this.signals.set([]);
          this.skipped.set([]);
          this.generatedAt.set(null);
          this.errorMessage.set(
            toUserFacingErrorMessage(error, {
              fallbackMessage: 'The advisor signals could not be loaded. Please try again.'
            })
          );
        }
      });
  }
}

function formatPercent(value: number | null): string {
  if (value === null) {
    return 'n/a';
  }
  const rounded = value.toFixed(2);
  return `${value > 0 ? '+' : ''}${rounded}%`;
}

function formatMoney(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return 'Unavailable';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}
