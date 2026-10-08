import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import {
  AddToWatchlistRequest,
  InstrumentOption,
  PriceAlert,
  TradeService,
  WatchlistGroup,
  WatchlistItem
} from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { currencyForSymbol, currencySymbolForDisplay } from '../instruments/instrument-currency';
import { ThemeService } from '../theme/theme.service';
import { NotificationBellComponent } from '../notifications/notification-bell.component';

/** Prices and alert counts are re-read this often while the page is open, as the inbox does. */
const REFRESH_INTERVAL_MS = 30_000;

/** The default watchlist every add without a group goes to; the API refuses to delete it. */
const FAVOURITE_WATCHLIST = 'favourite';

interface WatchlistRowView {
  item: WatchlistItem;
  lastPrice: number | null;
  changePct: number | null;
}

@Component({
  selector: 'app-watchlist',
  imports: [CommonModule, RouterLink, ReactiveFormsModule, NotificationBellComponent],
  templateUrl: './watchlist.component.html',
  styleUrl: './watchlist.component.css'
})
export class WatchlistComponent {
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

  readonly groups = signal<WatchlistGroup[]>([]);
  readonly selectedGroupId = signal<string | null>(null);
  readonly groupsLoading = signal(false);
  readonly groupsError = signal('');

  readonly watchlistRows = signal<WatchlistRowView[]>([]);
  readonly watchlistLoading = signal(false);
  readonly watchlistError = signal('');

  readonly addForm: FormGroup;
  readonly addFormSubmitting = signal(false);
  readonly addFormError = signal('');
  readonly showInstrumentDropdown = signal(false);
  readonly filteredInstrumentOptions = signal<InstrumentOption[]>([]);
  readonly addFormSuccess = signal('');

  readonly createGroupForm: FormGroup;
  readonly createGroupSubmitting = signal(false);
  readonly createGroupError = signal('');

  /** The custom watchlist awaiting delete confirmation, or null. */
  readonly groupPendingDelete = signal<string | null>(null);
  readonly groupDeleting = signal(false);
  readonly groupDeleteError = signal('');

  /** Read only for the per-row badge; alerts are managed on the Alerts page. */
  readonly priceAlerts = signal<PriceAlert[]>([]);

  /** Active alerts per watchlist entry, for the badge beside each symbol. */
  readonly activeAlertsByEntry = computed(() => {
    const counts = new Map<string, number>();
    for (const alert of this.priceAlerts()) {
      if (alert.status === 'ACTIVE') {
        counts.set(alert.watchlistId, (counts.get(alert.watchlistId) ?? 0) + 1);
      }
    }
    return counts;
  });

  readonly rowsForSelectedGroup = computed(() => {
    const groupId = this.selectedGroupId();
    if (groupId === null) {
      return this.watchlistRows();
    }
    return this.watchlistRows().filter((row) => row.item.watchlistGroupId === groupId);
  });

  readonly selectedGroupName = computed(() => {
    const groupId = this.selectedGroupId();
    const selected = this.groups().find((group) => group.watchlistGroupId === groupId);
    return selected?.name ?? 'Watchlist';
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
    // live prices come from the API's market-data consumer; re-read them and the alert counts quietly
    const refresh = setInterval(() => {
      this.loadWatchlist(true);
      this.loadPriceAlerts();
    }, REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      clearInterval(refresh);
    });

    this.addForm = this.fb.group({
      symbol: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(20)]]
    });

    this.createGroupForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(50)]]
    });

    this.loadGroupsAndWatchlist();
    this.loadPriceAlerts();
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

  selectGroup(groupId: string): void {
    this.selectedGroupId.set(groupId);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { groupId },
      queryParamsHandling: 'merge'
    });
    this.loadWatchlist();
  }

  clearGroupFilter(): void {
    this.selectedGroupId.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { groupId: null },
      queryParamsHandling: 'merge'
    });
    this.loadWatchlist();
  }

  createWatchlistGroup(): void {
    if (!this.createGroupForm.valid) {
      this.createGroupForm.markAllAsTouched();
      return;
    }

    const name = (this.createGroupForm.get('name')?.value ?? '').trim();
    if (name.length === 0) {
      return;
    }

    this.createGroupSubmitting.set(true);
    this.createGroupError.set('');

    this.tradeService.createWatchlistGroup({ name })
      .pipe(finalize(() => this.createGroupSubmitting.set(false)))
      .subscribe({
        next: (group) => {
          this.groups.update((groups) => [...groups, group]);
          this.createGroupForm.reset();
          this.selectGroup(group.watchlistGroupId);
        },
        error: (error) => {
          if (error.status === 409) {
            this.createGroupError.set('A watchlist with this name already exists.');
          } else {
            this.createGroupError.set('Unable to create watchlist right now.');
          }
        }
      });
  }

  isFavourite(group: WatchlistGroup): boolean {
    return group.name.trim().toLowerCase() === FAVOURITE_WATCHLIST;
  }

  requestDeleteGroup(group: WatchlistGroup): void {
    this.groupPendingDelete.set(group.watchlistGroupId);
    this.groupDeleteError.set('');
  }

  cancelDeleteGroup(): void {
    this.groupPendingDelete.set(null);
  }

  confirmDeleteGroup(group: WatchlistGroup): void {
    this.groupDeleting.set(true);
    this.groupDeleteError.set('');

    this.tradeService.deleteWatchlistGroup(group.watchlistGroupId)
      .pipe(finalize(() => this.groupDeleting.set(false)))
      .subscribe({
        next: () => {
          this.groupPendingDelete.set(null);
          const remaining = this.groups().filter((existing) => existing.watchlistGroupId !== group.watchlistGroupId);
          this.groups.set(remaining);
          if (this.selectedGroupId() === group.watchlistGroupId) {
            const fallback = remaining.find((existing) => this.isFavourite(existing)) ?? remaining[0];
            if (fallback) {
              this.selectGroup(fallback.watchlistGroupId);
            } else {
              this.clearGroupFilter();
            }
          } else {
            this.loadWatchlist(true);
          }
          // the watchlist's entries took their price alerts with them
          this.loadPriceAlerts();
        },
        error: (error) => {
          this.groupDeleteError.set(
            toUserFacingErrorMessage(error, { fallbackMessage: 'Unable to delete this watchlist right now.' })
          );
        }
      });
  }

  private loadGroupsAndWatchlist(): void {
    this.groupsLoading.set(true);
    this.groupsError.set('');

    this.tradeService.listWatchlistGroups()
      .pipe(finalize(() => this.groupsLoading.set(false)))
      .subscribe({
        next: (groups) => {
          this.groups.set(groups);

          const groupFromQueryRaw = this.route.snapshot.queryParamMap.get('groupId');
          const groupFromQuery = groupFromQueryRaw ? groupFromQueryRaw : null;
          if (groupFromQuery !== null && groups.some((group) => group.watchlistGroupId === groupFromQuery)) {
            this.selectedGroupId.set(groupFromQuery);
          } else if (groups.length > 0) {
            this.selectedGroupId.set(groups[0].watchlistGroupId);
          }

          this.loadWatchlist();
        },
        error: () => {
          this.groupsError.set('Unable to load watchlists.');
          this.loadWatchlist();
        }
      });
  }

  /** @param silent a background refresh: keeps the table on screen instead of showing "Loading" */
  private loadWatchlist(silent = false): void {
    if (!this.claims()) {
      return;
    }

    if (!silent) {
      this.watchlistLoading.set(true);
      this.watchlistError.set('');
    }

    this.tradeService.getWatchlist(this.claims()!.accountId, this.selectedGroupId() ?? undefined)
      .pipe(finalize(() => this.watchlistLoading.set(false)))
      .subscribe({
        next: (items) => {
          this.watchlistRows.set(
            items.map((item) => ({
              item,
              lastPrice: item.lastPrice,
              changePct: item.changePct
            }))
          );
        },
        error: () => {
          this.watchlistError.set('Failed to load watchlist. Please try again.');
        }
      });
  }

  onInstrumentInput(): void {
    const query = (this.addForm.get('symbol')?.value ?? '').trim();
    if (query.length === 0) {
      this.filteredInstrumentOptions.set([]);
      this.showInstrumentDropdown.set(false);
      return;
    }

    this.tradeService.getInstruments(this.claims()!.accountId).subscribe({
      next: (options) => {
        const normalizedQuery = query.toUpperCase();
        const filtered = options
          .filter((option) =>
            option.symbol.toUpperCase().includes(normalizedQuery)
            || option.name.toUpperCase().includes(normalizedQuery)
          )
          .sort((a, b) => a.name.localeCompare(b.name) || a.symbol.localeCompare(b.symbol));
        this.filteredInstrumentOptions.set(filtered);
        this.showInstrumentDropdown.set(filtered.length > 0);
      },
      error: () => {
        this.filteredInstrumentOptions.set([]);
        this.showInstrumentDropdown.set(false);
      }
    });
  }

  onInstrumentFocus(): void {
    const symbol = this.addForm.get('symbol')?.value || '';
    if (symbol.length > 0 && this.filteredInstrumentOptions().length > 0) {
      this.showInstrumentDropdown.set(true);
    }
  }

  onInstrumentBlur(): void {
    // Delay to allow click on dropdown item
    setTimeout(() => this.showInstrumentDropdown.set(false), 200);
  }

  selectInstrument(option: InstrumentOption): void {
    this.addForm.patchValue({ symbol: option.symbol });
    this.showInstrumentDropdown.set(false);
  }

  submitAddForm(): void {
    if (!this.addForm.valid || !this.claims()) {
      this.addForm.markAllAsTouched();
      return;
    }

    const symbol = (this.addForm.get('symbol')?.value ?? '').trim().toUpperCase();
    if (symbol.length === 0) {
      this.addForm.markAllAsTouched();
      return;
    }

    this.addFormSubmitting.set(true);
    this.addFormError.set('');
    this.addFormSuccess.set('');

    const request: AddToWatchlistRequest = {
      accountId: this.claims()!.accountId,
      symbol,
      watchlistGroupId: this.selectedGroupId() ?? undefined
    };

    this.tradeService.addToWatchlist(request)
      .pipe(finalize(() => this.addFormSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.addForm.reset();
          this.addFormSuccess.set(`${request.symbol} added to ${this.selectedGroupName()}`);
          setTimeout(() => this.addFormSuccess.set(''), 3000);
          this.loadWatchlist();
        },
        error: (error) => {
          if (error.status === 401 || error.status === 403) {
            this.addFormError.set('Your session is invalid or expired. Please sign in again.');
          } else if (error.status === 409) {
            this.addFormError.set('This symbol is already in the selected watchlist.');
          } else if (error.status === 404) {
            this.addFormError.set('Instrument not found.');
          } else {
            this.addFormError.set('Failed to add to watchlist. Please try again.');
          }
        }
      });
  }

  removeFromWatchlist(row: WatchlistRowView): void {
    this.tradeService.removeFromWatchlist(row.item.watchlistId).subscribe({
      next: () => {
        this.watchlistRows.update((rows) => rows.filter((existing) => existing.item.watchlistId !== row.item.watchlistId));
        // the entry's price alerts were removed with it
        this.loadPriceAlerts();
      },
      error: () => {
        this.watchlistError.set('Failed to remove item. Please try again.');
      }
    });
  }

  trade(row: WatchlistRowView): void {
    void this.router.navigate(['/orders'], { queryParams: { symbol: row.item.symbol } });
  }

  /** Opens the Alerts page with this instrument already chosen in its form. */
  setAlert(row: WatchlistRowView): void {
    void this.router.navigate(['/alerts'], { queryParams: { watchlistId: row.item.watchlistId } });
  }

  activeAlertsFor(row: WatchlistRowView): number {
    return this.activeAlertsByEntry().get(row.item.watchlistId) ?? 0;
  }

  /** ₹ / $ for the currency the instrument is quoted in; the symbol scheme if the API omits it. */
  currencySymbol(row: WatchlistRowView): string {
    return currencySymbolForDisplay(row.item.currency ?? currencyForSymbol(row.item.symbol));
  }

  /** Badge counts only; a failure leaves the last counts in place rather than erroring the page. */
  private loadPriceAlerts(): void {
    if (!this.claims()) {
      return;
    }
    this.tradeService.listPriceAlerts(this.claims()!.accountId).subscribe({
      next: (list) => this.priceAlerts.set(list.alerts),
      error: () => undefined
    });
  }
}
