import { Injectable, inject, signal } from '@angular/core';

import { TradeService } from '../api/trade.service';

/** Unread notification count shared by the header bell and the inbox page. */
@Injectable({ providedIn: 'root' })
export class NotificationCountService {
  private readonly tradeService = inject(TradeService);

  readonly unreadCount = signal(0);

  refresh(accountId: number): void {
    this.tradeService.getNotifications(accountId, undefined, 100).subscribe({
      next: (notifications) => this.unreadCount.set(notifications.filter((n) => n.readAt === null).length),
      // the badge is a hint, not a feature: keep the last known count rather than surface an error
      error: () => undefined
    });
  }

  set(count: number): void {
    this.unreadCount.set(Math.max(0, count));
  }
}
