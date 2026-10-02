import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, Input, OnChanges, OnInit, SimpleChanges, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { OrderHistoryEntry, OrderStatus } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';

export const BLOTTER_POLL_INTERVAL_MS = 3_000;
export const BLOTTER_MAX_POLL_ATTEMPTS = 10;

const ORDER_ERROR_MESSAGES: Record<string, string> = {
  'ACC-404': 'The account could not be found.',
  'ACC-403': 'This account is not active, or it is not the account this sign-in may trade.',
  'INS-404': 'That instrument is not available for trading.',
  'ORD-400': 'This order could not be placed because there is not enough cash.',
  'ORD-409': 'This order conflicts with your holdings or a duplicate request was received.',
  'VAL-422': 'One or more fields are not acceptable. Check symbol, side, quantity and price.',
  'AUTH-401': 'Your session is not authorised. Sign in again and retry.',
  'AUTH-409': 'That username already exists.'
};

@Component({
  selector: 'app-blotter',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './blotter.component.html',
  styleUrl: './blotter.component.css'
})
export class BlotterComponent implements OnInit, OnChanges {
  private readonly tradeService = inject(TradeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);

  private historyRequestInFlight = false;
  private pollTimerId: ReturnType<typeof setTimeout> | null = null;
  private pollAttemptCount = 0;

  @Input() accountId: number | null | undefined = undefined;

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');

  readonly orderHistory = signal<OrderHistoryEntry[]>([]);
  readonly historyLoading = signal(false);
  readonly historyErrorMessage = signal('');
  readonly pollingActive = signal(false);
  readonly pollAttemptsUsed = signal(0);
  readonly maxPollAttempts = BLOTTER_MAX_POLL_ATTEMPTS;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
    });
  }

  ngOnInit(): void {
    this.fetchOrderHistory({ showLoading: true, resetPollingBound: true });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['accountId'] && !changes['accountId'].firstChange) {
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

  private toReadableHistoryError(error: unknown): string {
    const fallback = 'Order history could not be loaded. Please try again.';

    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (error.status === 0) {
      return 'The service could not be reached. Check whether Trade API is running and CORS allows this UI origin.';
    }

    const errorCode = this.readErrorCode(error.error);
    if (!errorCode) {
      return fallback;
    }

    return ORDER_ERROR_MESSAGES[errorCode] ?? fallback;
  }

  private readErrorCode(body: unknown): string | null {
    if (typeof body !== 'object' || body === null || !('errorCode' in body)) {
      return null;
    }

    const errorCode = (body as { errorCode?: unknown }).errorCode;
    return typeof errorCode === 'string' ? errorCode : null;
  }
}
