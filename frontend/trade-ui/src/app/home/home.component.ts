import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { OrderHistoryEntry } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly now = signal(Date.now());

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly recentOrders = signal<OrderHistoryEntry[]>([]);
  readonly ordersLoading = signal(false);
  readonly ordersError = signal('');

  readonly greeting = computed(() => {
    const hour = new Date(this.now()).getHours();
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  });

  /** Seconds until the access token expires, or null when it can't be read. */
  readonly secondsLeft = computed(() => {
    const exp = this.claims()?.exp;
    return exp === undefined ? null : Math.max(0, Math.floor(exp - this.now() / 1000));
  });

  readonly sessionExpired = computed(() => this.secondsLeft() === 0);

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    this.loadRecentOrders();
  }

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  statusLabel(status: OrderHistoryEntry['status']): string {
    return status === 'NEW' ? 'NEW - Working' : status;
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
          const sorted = [...orders].sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn));
          this.recentOrders.set(sorted.slice(0, 5));
        },
        error: () => {
          this.ordersError.set('Could not load recent orders.');
          this.recentOrders.set([]);
        }
      });
  }
}
