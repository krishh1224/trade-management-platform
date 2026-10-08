import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { readErrorCode, toUserFacingErrorMessage } from '../api/error-catalogue';
import { AlertDirection, PriceAlert, TradeService, WatchlistItem } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { currencyForSymbol, currencySymbolForDisplay } from '../instruments/instrument-currency';
import { NotificationBellComponent } from '../notifications/notification-bell.component';
import { ThemeService } from '../theme/theme.service';

/** Alert states and prices are re-read this often while the page is open, as the inbox does. */
const REFRESH_INTERVAL_MS = 30_000;

/** Greater than zero with at most 4 decimal places, as contracts/watchlists-api.yaml requires. */
const THRESHOLD_PATTERN = /^(?!0+(\.0+)?$)\d{1,15}(\.\d{1,4})?$/;

export type AlertFilter = 'ALL' | 'ACTIVE' | 'TRIGGERED';

/**
 * Price alerts: create one on a watched instrument, see which are active and which have fired, delete
 * them. Alerts belong to watchlist entries (the API refuses anything else), so the instrument choice
 * is the account's watched instruments. The Watchlist page links here with ?watchlistId= to preselect.
 */
@Component({
  selector: 'app-alerts',
  imports: [CommonModule, RouterLink, ReactiveFormsModule, NotificationBellComponent],
  templateUrl: './alerts.component.html',
  // the shared page shell (top bar, panels, tables, buttons) lives with the Watchlist page
  styleUrls: ['../watchlist/watchlist.component.css', './alerts.component.css']
})
export class AlertsComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly themeService = inject(ThemeService);
  private readonly fb = inject(FormBuilder);
  private readonly now = signal(Date.now());

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly profileMenuOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;

  readonly priceAlerts = signal<PriceAlert[]>([]);
  readonly maxActiveAlerts = signal(0);
  readonly activeAlertCount = signal(0);
  readonly alertsLoading = signal(false);
  readonly alertsError = signal('');
  readonly filter = signal<AlertFilter>('ALL');

  /** One entry per watched symbol: an alert is per symbol, whichever watchlist holds it. */
  readonly watchedInstruments = signal<WatchlistItem[]>([]);
  readonly instrumentsLoading = signal(false);
  readonly instrumentsError = signal('');

  readonly alertForm: FormGroup;
  readonly selectedSymbol = signal<string | null>(null);
  readonly alertFormSubmitting = signal(false);
  readonly alertFormError = signal('');
  readonly alertFormSuccess = signal('');

  readonly atAlertLimit = computed(() => this.maxActiveAlerts() > 0 && this.activeAlertCount() >= this.maxActiveAlerts());

  readonly selectedInstrument = computed(() =>
    this.watchedInstruments().find((item) => item.symbol === this.selectedSymbol()) ?? null
  );

  readonly triggeredCount = computed(() => this.priceAlerts().filter((alert) => alert.status === 'TRIGGERED').length);

  readonly visibleAlerts = computed(() => {
    const filter = this.filter();
    return filter === 'ALL' ? this.priceAlerts() : this.priceAlerts().filter((alert) => alert.status === filter);
  });

  /** Seconds until the access token expires, or null when it can't be read. */
  readonly secondsLeft = computed(() => {
    const exp = this.claims()?.exp;
    return exp === undefined ? null : Math.max(0, Math.floor(exp - this.now() / 1000));
  });

  readonly sessionExpired = computed(() => this.secondsLeft() === 0);

  constructor() {
    this.themeService.initialize();
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    const refresh = setInterval(() => {
      this.loadPriceAlerts(true);
      this.loadWatchedInstruments(true);
    }, REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      clearInterval(refresh);
    });

    this.alertForm = this.fb.group({
      symbol: [null as string | null, Validators.required],
      direction: ['ABOVE' as AlertDirection, Validators.required],
      threshold: [null as number | null, [Validators.required, Validators.pattern(THRESHOLD_PATTERN)]]
    });
    this.alertForm.get('symbol')!.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((symbol: string | null) => this.selectedSymbol.set(symbol));

    this.loadPriceAlerts();
    this.loadWatchedInstruments();
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

  setFilter(filter: AlertFilter): void {
    this.filter.set(filter);
  }

  /** ₹ / $ for an ISO code, falling back to the symbol scheme when the API sends none. */
  currencySymbol(currency: string | null | undefined, symbol: string): string {
    return currencySymbolForDisplay(currency ?? currencyForSymbol(symbol));
  }

  submitAlertForm(): void {
    if (!this.claims()) {
      return;
    }
    if (!this.alertForm.valid) {
      this.alertForm.markAllAsTouched();
      return;
    }

    const target = this.selectedInstrument();
    if (!target) {
      this.alertForm.markAllAsTouched();
      return;
    }
    const direction = this.alertForm.get('direction')?.value as AlertDirection;
    const threshold = Number(this.alertForm.get('threshold')?.value);

    // the API refuses the same active level twice (WTL-409); say so without the round trip
    const duplicate = this.priceAlerts().some((alert) => alert.status === 'ACTIVE'
      && alert.symbol === target.symbol
      && alert.direction === direction
      && Number(alert.threshold) === threshold);
    if (duplicate) {
      this.alertFormError.set(this.duplicateAlertMessage(target.symbol));
      return;
    }

    this.alertFormSubmitting.set(true);
    this.alertFormError.set('');
    this.alertFormSuccess.set('');

    this.tradeService.createPriceAlert(this.claims()!.accountId, {
      watchlistId: target.watchlistId,
      direction,
      threshold
    })
      .pipe(finalize(() => this.alertFormSubmitting.set(false)))
      .subscribe({
        next: (alert) => {
          this.alertForm.patchValue({ threshold: null });
          this.alertForm.get('threshold')?.markAsUntouched();
          const movement = alert.direction === 'ABOVE' ? 'rises to' : 'falls to';
          this.alertFormSuccess.set('Alert set: ' + alert.symbol + ' ' + movement + ' '
            + this.currencySymbol(alert.currency, alert.symbol) + alert.threshold + '.');
          setTimeout(() => this.alertFormSuccess.set(''), 4000);
          this.loadPriceAlerts();
        },
        error: (error) => {
          // the form was valid, so VAL-422 here is the active-alert limit (another tab may have used it up)
          const code = readErrorCode(error);
          if (code === 'VAL-422') {
            this.alertFormError.set(this.alertLimitMessage());
            this.loadPriceAlerts(true);
          } else if (code === 'WTL-409') {
            this.alertFormError.set(this.duplicateAlertMessage(target.symbol));
            this.loadPriceAlerts(true);
          } else {
            this.alertFormError.set(
              toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to create the price alert. Please try again.' })
            );
          }
        }
      });
  }

  deletePriceAlert(alert: PriceAlert): void {
    if (!this.claims()) {
      return;
    }
    this.alertsError.set('');
    this.tradeService.deletePriceAlert(this.claims()!.accountId, alert.alertId).subscribe({
      next: () => {
        this.priceAlerts.update((alerts) => alerts.filter((existing) => existing.alertId !== alert.alertId));
        if (alert.status === 'ACTIVE') {
          this.activeAlertCount.update((count) => Math.max(0, count - 1));
        }
      },
      error: (error) => {
        this.alertsError.set(
          toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to delete the price alert. Please try again.' })
        );
      }
    });
  }

  duplicateAlertMessage(symbol: string): string {
    return 'You already have an active alert on ' + symbol + ' at this price in this direction.';
  }

  alertLimitMessage(): string {
    const limit = this.maxActiveAlerts() > 0 ? String(this.maxActiveAlerts()) : 'the maximum number of';
    return 'You already have ' + limit + ' active price alerts. Delete one to set another.';
  }

  /** @param silent a background refresh: keeps the list on screen and does not replace an error */
  private loadPriceAlerts(silent = false): void {
    if (!this.claims()) {
      return;
    }
    if (!silent) {
      this.alertsLoading.set(true);
      this.alertsError.set('');
    }

    this.tradeService.listPriceAlerts(this.claims()!.accountId)
      .pipe(finalize(() => this.alertsLoading.set(false)))
      .subscribe({
        next: (list) => {
          this.priceAlerts.set(list.alerts);
          this.maxActiveAlerts.set(list.maxActiveAlerts);
          this.activeAlertCount.set(list.activeAlerts);
        },
        error: (error) => {
          if (!silent) {
            this.alertsError.set(
              toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load price alerts. Please try again.' })
            );
          }
        }
      });
  }

  private loadWatchedInstruments(silent = false): void {
    if (!this.claims()) {
      return;
    }
    if (!silent) {
      this.instrumentsLoading.set(true);
      this.instrumentsError.set('');
    }

    this.tradeService.getWatchlist(this.claims()!.accountId)
      .pipe(finalize(() => this.instrumentsLoading.set(false)))
      .subscribe({
        next: (items) => {
          const bySymbol = new Map<string, WatchlistItem>();
          for (const item of items) {
            if (!bySymbol.has(item.symbol)) {
              bySymbol.set(item.symbol, item);
            }
          }
          const instruments = [...bySymbol.values()].sort((a, b) => a.symbol.localeCompare(b.symbol));
          this.watchedInstruments.set(instruments);
          if (!silent) {
            this.preselect(items, instruments);
          }
        },
        error: (error) => {
          if (!silent) {
            this.instrumentsError.set(
              toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load your watched instruments.' })
            );
          }
        }
      });
  }

  /** The instrument the Watchlist page asked for (?watchlistId=), else the first one. */
  private preselect(entries: WatchlistItem[], instruments: WatchlistItem[]): void {
    if (this.alertForm.get('symbol')?.value) {
      return;
    }
    const requested = this.route.snapshot.queryParamMap.get('watchlistId');
    const symbol = entries.find((entry) => entry.watchlistId === requested)?.symbol ?? instruments[0]?.symbol ?? null;
    this.alertForm.patchValue({ symbol });
  }
}
