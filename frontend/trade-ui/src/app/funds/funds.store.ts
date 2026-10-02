import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, forkJoin, tap } from 'rxjs';

import {
  BalanceResponse,
  CashMovementType,
  CashTransactionResponse,
  ErrorResponse
} from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';

// number of recent transfers shown in the dialog
export const ACTIVITY_LIMIT = 10;

const SERVICE_DOWN = "We can't reach the trading service right now. Check your connection and try again in a moment.";

// messages shown to the user, keyed by error code from the contract
const FUNDS_ERRORS: Partial<Record<ErrorResponse['errorCode'], string>> = {
  'CSH-400': "That's more than you can withdraw right now. Cash held for working BUY orders can't be withdrawn.",
  'CSH-409':
    'This transfer was already submitted, or your balance changed at the same moment. Your balance has been refreshed, so check it before trying again.',
  'VAL-422': 'Enter an amount above zero with at most two decimals.',
  'ACC-403': "This account can't move money right now. It may be suspended, or your session is for a different account.",
  'ACC-404': "We couldn't find this account. Sign out and back in; if it keeps happening, contact the desk.",
  'AUTH-401': 'Your session has ended. Sign in again to move money.'
};

// status 0 means the request never reached the server
export function describeFundsError(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return SERVICE_DOWN;
  }

  const code = errorCodeOf(error);
  const known = code ? FUNDS_ERRORS[code as ErrorResponse['errorCode']] : undefined;
  if (known) {
    return known;
  }

  return code
    ? `Something went wrong on our side (code ${code}). Check your balance before trying again.`
    : 'Something went wrong on our side. Check your balance before trying again.';
}

export function errorCodeOf(error: unknown): string | null {
  if (error instanceof HttpErrorResponse && typeof error.error?.errorCode === 'string') {
    return error.error.errorCode;
  }
  return null;
}

// wallet balance and recent transfers for the logged in account
@Injectable({ providedIn: 'root' })
export class FundsStore {
  private readonly trade = inject(TradeService);
  private readonly session = inject(AuthSessionService);

  readonly accountId = computed(() => this.session.claims()?.accountId ?? null);

  readonly balance = signal<BalanceResponse | null>(null);
  readonly activity = signal<CashTransactionResponse[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal('');

  readonly currency = computed(() => this.balance()?.currency ?? 'INR');
  readonly available = computed(() => this.balance()?.availableToWithdraw ?? 0);
  readonly held = computed(() => this.balance()?.heldForOrders ?? 0);

  // load balance and history together
  load(): void {
    const accountId = this.accountId();
    if (accountId === null) {
      this.loadError.set('Your session has ended. Sign in again to move money.');
      return;
    }

    this.loading.set(true);
    this.loadError.set('');
    forkJoin({
      balance: this.trade.getBalance(accountId),
      activity: this.trade.getCashTransactions(accountId, ACTIVITY_LIMIT)
    }).subscribe({
      next: ({ balance, activity }) => {
        this.balance.set(balance);
        this.activity.set(activity);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(describeFundsError(error));
        this.loading.set(false);
      }
    });
  }

  // caller passes the idempotency key so a retry doesn't move money twice
  move(type: CashMovementType, amount: number, idempotencyKey: string): Observable<CashTransactionResponse> {
    const accountId = this.accountId();
    if (accountId === null) {
      throw new Error('No signed-in account');
    }

    const body = { amount, idempotencyKey };
    const request = type === 'DEPOSIT' ? this.trade.deposit(accountId, body) : this.trade.withdraw(accountId, body);
    return request.pipe(tap(() => this.load()));
  }
}
