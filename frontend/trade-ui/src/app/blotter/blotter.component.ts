import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, Input, OnChanges, OnInit, SimpleChanges, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { OrderHistoryEntry, OrderStatus } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { currencyForSymbol } from '../instruments/instrument-currency';
import { ThemeService } from '../theme/theme.service';
import { FooterComponent } from '../shared/footer.component';

export const BLOTTER_POLL_INTERVAL_MS = 3_000;
export const BLOTTER_MAX_POLL_ATTEMPTS = 10;

type StatusFilter = 'ALL' | 'FILLED' | 'REJECTED' | 'NEW';
type SideFilter = 'ALL' | 'BUY' | 'SELL';
type CreatedAtFilter = 'ALL' | 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS';

@Component({
  selector: 'app-blotter',
  imports: [DatePipe, DecimalPipe, RouterLink, FooterComponent],
  templateUrl: './blotter.component.html',
  styleUrl: './blotter.component.css'
})
export class BlotterComponent implements OnInit, OnChanges {
  private readonly tradeService = inject(TradeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);

  private historyRequestInFlight = false;
  private pollTimerId: ReturnType<typeof setTimeout> | null = null;
  private pollAttemptCount = 0;

  @Input() accountId: number | null | undefined = undefined;

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly profileMenuOpen = signal(false);
  readonly theme = this.themeService.theme;

  readonly orderHistory = signal<OrderHistoryEntry[]>([]);
  readonly historyLoading = signal(false);
  readonly historyErrorMessage = signal('');
  readonly currentCashBalance = signal<number | null>(null);
  readonly cashAfterCurrency = signal('INR');
  readonly cashAfterLoading = signal(false);
  readonly pollingActive = signal(false);
  readonly pollAttemptsUsed = signal(0);
  readonly maxPollAttempts = BLOTTER_MAX_POLL_ATTEMPTS;
  readonly selectedStatusFilter = signal<StatusFilter>('ALL');
  readonly selectedSideFilter = signal<SideFilter>('ALL');
  readonly selectedCreatedAtFilter = signal<CreatedAtFilter>('ALL');
  readonly newOrderCount = computed(() => this.orderHistory().filter((entry) => entry.status === 'NEW').length);
  readonly rejectedOrderCount = computed(() => this.orderHistory().filter((entry) => entry.status === 'REJECTED').length);
  readonly filledOrderCount = computed(() => this.orderHistory().filter((entry) => entry.status === 'FILLED').length);
  readonly filteredOrderHistory = computed(() =>
    this.orderHistory().filter((entry) => {
      const statusFilter = this.selectedStatusFilter();
      if (statusFilter !== 'ALL' && entry.status !== statusFilter) {
        return false;
      }

      const sideFilter = this.selectedSideFilter();
      if (sideFilter !== 'ALL' && entry.side !== sideFilter) {
        return false;
      }

      return this.matchesCreatedAtFilter(entry.createdOn, this.selectedCreatedAtFilter());
    })
  );
  readonly cashAfterByOrderId = computed(() => {
    const currentBalance = this.currentCashBalance();
    if (currentBalance === null) {
      return new Map<string, number>();
    }

    let rollback = 0;
    const amounts = new Map<string, number>();
    for (const entry of this.orderHistory()) {
      const rowCashAfter = currentBalance - rollback;
      amounts.set(entry.orderId, rowCashAfter);

      if (entry.status === 'FILLED') {
        const fillPrice = entry.executedPrice ?? entry.price;
        const tradeValue = fillPrice * entry.quantity;
        rollback += entry.side === 'BUY' ? -tradeValue : tradeValue;
      }
    }

    return amounts;
  });

  constructor() {
    this.themeService.initialize();
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
    });
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

  ngOnInit(): void {
    this.loadCashAfter();
    this.fetchOrderHistory({ showLoading: true, resetPollingBound: true });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['accountId'] && !changes['accountId'].firstChange) {
      this.loadCashAfter();
      this.fetchOrderHistory({ showLoading: true, resetPollingBound: true });
    }
  }

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  refreshOrderHistory(): void {
    this.fetchOrderHistory({ showLoading: true, resetPollingBound: true });
  }

  setStatusFilter(filter: StatusFilter): void {
    this.selectedStatusFilter.update((current) => (current === filter ? 'ALL' : filter));
  }

  setSideFilter(filter: SideFilter): void {
    this.selectedSideFilter.set(filter);
  }

  setCreatedAtFilter(filter: CreatedAtFilter): void {
    this.selectedCreatedAtFilter.set(filter);
  }

  isStatusFilterActive(filter: StatusFilter): boolean {
    return this.selectedStatusFilter() === filter;
  }

  clearFilters(): void {
    this.selectedStatusFilter.set('ALL');
    this.selectedSideFilter.set('ALL');
    this.selectedCreatedAtFilter.set('ALL');
  }

  cashAfterFor(orderId: string): number | null {
    return this.cashAfterByOrderId().get(orderId) ?? null;
  }

  statusBadgeText(status: OrderStatus): string {
    switch (status) {
      case 'NEW':
        return 'NEW - Working';
      case 'FILLED':
        return 'FILLED';
      case 'REJECTED':
        return 'REJECTED';
      case 'CANCELLED':
        return 'CANCELLED';
      default:
        return status;
    }
  }

  currencyForSymbol(symbol: string): string {
    return currencyForSymbol(symbol);
  }

  private fetchOrderHistory(options: { showLoading: boolean; resetPollingBound: boolean }): void {
    const accountId = this.resolveAccountId();
    if (accountId === null) {
      this.orderHistory.set([]);
      this.historyErrorMessage.set('The signed-in account could not be read from this session. Please sign in again.');
      this.historyLoading.set(false);
      this.stopPolling();
      return;
    }

    if (this.historyRequestInFlight) {
      return;
    }

    if (options.resetPollingBound) {
      this.resetPollingBound();
      this.stopPolling();
    }

    this.historyRequestInFlight = true;
    this.historyErrorMessage.set('');
    if (options.showLoading) {
      this.historyLoading.set(true);
    }

    this.tradeService
      .getOrders(accountId)
      .pipe(
        finalize(() => {
          this.historyRequestInFlight = false;
          this.historyLoading.set(false);
        })
      )
      .subscribe({
        next: (entries) => {
          const sortedEntries = [...entries].sort((left, right) => this.toEpochMillis(right.createdOn) - this.toEpochMillis(left.createdOn));
          this.orderHistory.set(sortedEntries);
          this.handlePostHistoryFetch(sortedEntries);
        },
        error: (error: unknown) => {
          this.historyErrorMessage.set(this.toReadableHistoryError(error));
          this.stopPolling();
        }
      });
  }

  private resolveAccountId(): number | null {
    if (this.accountId !== undefined) {
      return this.accountId;
    }

    return this.authSession.claims()?.accountId ?? null;
  }

  private loadCashAfter(): void {
    const accountId = this.resolveAccountId();
    if (accountId === null) {
      this.currentCashBalance.set(null);
      return;
    }

    this.cashAfterLoading.set(true);
    this.tradeService
      .getBalance(accountId)
      .pipe(finalize(() => this.cashAfterLoading.set(false)))
      .subscribe({
        next: (balance) => {
          this.currentCashBalance.set(balance.cashBalance);
          this.cashAfterCurrency.set(balance.currency || 'INR');
        },
        error: () => {
          this.currentCashBalance.set(null);
        }
      });
  }

  private handlePostHistoryFetch(entries: OrderHistoryEntry[]): void {
    const hasWorkingOrders = entries.some((entry) => entry.status === 'NEW');
    if (!hasWorkingOrders) {
      this.stopPolling();
      return;
    }

    if (this.pollAttemptCount >= BLOTTER_MAX_POLL_ATTEMPTS) {
      this.pollingActive.set(false);
      return;
    }

    this.scheduleNextPoll();
  }

  private scheduleNextPoll(): void {
    if (this.pollTimerId !== null) {
      return;
    }

    this.pollingActive.set(true);
    this.pollTimerId = setTimeout(() => {
      this.pollTimerId = null;
      this.pollAttemptCount += 1;
      this.pollAttemptsUsed.set(this.pollAttemptCount);
      this.fetchOrderHistory({ showLoading: false, resetPollingBound: false });
    }, BLOTTER_POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimerId !== null) {
      clearTimeout(this.pollTimerId);
      this.pollTimerId = null;
    }

    this.pollingActive.set(false);
  }

  private resetPollingBound(): void {
    this.pollAttemptCount = 0;
    this.pollAttemptsUsed.set(0);
  }

  private toEpochMillis(value: string): number {
    const millis = Date.parse(value);
    return Number.isNaN(millis) ? 0 : millis;
  }

  private matchesCreatedAtFilter(createdOn: string, filter: CreatedAtFilter): boolean {
    if (filter === 'ALL') {
      return true;
    }

    const createdMs = this.toEpochMillis(createdOn);
    if (createdMs === 0) {
      return false;
    }

    const created = new Date(createdMs);
    const now = new Date();

    if (filter === 'TODAY') {
      return (
        created.getFullYear() === now.getFullYear() &&
        created.getMonth() === now.getMonth() &&
        created.getDate() === now.getDate()
      );
    }

    const lookbackMs = filter === 'LAST_7_DAYS' ? 7 * 24 * 60 * 60 * 1000 : 30 * 24 * 60 * 60 * 1000;
    return createdMs >= now.getTime() - lookbackMs;
  }

  private toReadableHistoryError(error: unknown): string {
    return toUserFacingErrorMessage(error, { fallbackMessage: 'Order history could not be loaded. Please try again.' });
  }
}
