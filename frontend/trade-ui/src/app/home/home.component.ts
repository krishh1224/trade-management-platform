import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AccountStatus, OrderHistoryEntry, PositionResponse } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { FundsDialogComponent } from '../funds/funds-dialog.component';
import { currencyForSymbol } from '../instruments/instrument-currency';
import { ThemeService } from '../theme/theme.service';
import { FooterComponent } from '../shared/footer.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, DecimalPipe, FooterComponent, FundsDialogComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly now = signal(Date.now());

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly fundsOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly profileMenuOpen = signal(false);
  readonly customerName = signal('Customer');
  readonly theme = this.themeService.theme;
  readonly balanceAmount = signal<number | null>(null);
  readonly balanceCurrency = signal('INR');
  readonly balanceLoading = signal(false);
  readonly balanceHidden = signal(true);
  readonly accountStatus = signal<AccountStatus | null>(null);
  readonly accountStatusLoading = signal(false);
  readonly positions = signal<PositionResponse[]>([]);
  readonly positionsLoading = signal(false);
  readonly positionsError = signal('');
  readonly recentOrders = signal<OrderHistoryEntry[]>([]);
  readonly ordersLoading = signal(false);
  readonly ordersError = signal('');

  readonly greetingSalutation = computed(() => {
    const hour = new Date(this.now()).getHours();
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  });

  readonly greeting = computed(() => {
    return `${this.greetingSalutation()}, ${this.customerName()}`;
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
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    this.loadCustomerName();
    this.loadBalance();
    this.loadAccountStatus();
    this.loadPositions();
    this.loadRecentOrders();
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

  toggleBalanceVisibility(): void {
    this.balanceHidden.update((hidden) => !hidden);
  }

  statusLabel(status: OrderHistoryEntry['status']): string {
    return status === 'NEW' ? 'NEW - Working' : status;
  }

  currencyForSymbol(symbol: string): string {
    return currencyForSymbol(symbol);
  }

  private loadRecentOrders(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.recentOrders.set([]);
      return;
    }

    this.ordersLoading.set(true);
    this.ordersError.set('');

    this.tradeService
      .getOrders(accountId)
      .pipe(finalize(() => this.ordersLoading.set(false)))
      .subscribe({
        next: (orders) => {
          const filledOnly = orders.filter((order) => order.status === 'FILLED');
          const sorted = [...filledOnly].sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn));
          this.recentOrders.set(sorted.slice(0, 5));
        },
        error: () => {
          this.ordersError.set('Could not load recent orders.');
          this.recentOrders.set([]);
        }
      });
  }

  private loadPositions(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.positions.set([]);
      return;
    }

    this.positionsLoading.set(true);
    this.positionsError.set('');

    this.tradeService
      .getPositions(accountId)
      .pipe(finalize(() => this.positionsLoading.set(false)))
      .subscribe({
        next: (positions) => {
          const sorted = [...positions].sort((a, b) => a.symbol.localeCompare(b.symbol));
          this.positions.set(sorted);
        },
        error: () => {
          this.positionsError.set('Could not load portfolio holdings.');
          this.positions.set([]);
        }
      });
  }

  private loadBalance(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.balanceAmount.set(null);
      return;
    }

    this.balanceLoading.set(true);
    this.tradeService
      .getBalance(accountId)
      .pipe(finalize(() => this.balanceLoading.set(false)))
      .subscribe({
        next: (balance) => {
          this.balanceAmount.set(balance.cashBalance);
          this.balanceCurrency.set(balance.currency || 'INR');
        },
        error: () => {
          this.balanceAmount.set(null);
        }
      });
  }

  private loadCustomerName(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.customerName.set(this.claims()?.sub ?? 'Customer');
      return;
    }

    this.tradeService.getAccount(accountId).subscribe({
      next: (account) => {
        this.customerName.set(account.holderName || this.claims()?.sub || 'Customer');
      },
      error: () => {
        this.customerName.set(this.claims()?.sub ?? 'Customer');
      }
    });
  }

  private loadAccountStatus(): void {
    const accountId = this.claims()?.accountId ?? null;
    if (accountId === null) {
      this.accountStatus.set(null);
      return;
    }

    this.accountStatusLoading.set(true);
    this.tradeService
      .getAccount(accountId)
      .pipe(finalize(() => this.accountStatusLoading.set(false)))
      .subscribe({
        next: (account) => {
          this.accountStatus.set(account.status);
        },
        error: () => {
          this.accountStatus.set(null);
        }
      });
  }
}
