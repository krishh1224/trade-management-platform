import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CustomerNotification, NotificationType, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationCountService } from './notification-count.service';

export type InboxFilter = 'ALL' | 'UNREAD';

const REFRESH_INTERVAL_MS = 30_000;

const CHANNEL_LABELS: Record<string, string> = {
  EMAIL: 'email',
  SMS: 'SMS',
  PUSH: 'push notification',
  BROWSER: 'in-app'
};

@Component({
  selector: 'app-notifications',
  imports: [CommonModule, RouterLink, NotificationBellComponent],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css'
})
export class NotificationsComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly countService = inject(NotificationCountService);
  private readonly now = signal(Date.now());

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly profileMenuOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;

  readonly notifications = signal<CustomerNotification[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly filter = signal<InboxFilter>('ALL');
  readonly markingRead = signal<ReadonlySet<number>>(new Set());

  readonly unreadCount = computed(() => this.notifications().filter((n) => n.readAt === null).length);

  readonly visibleNotifications = computed(() =>
    this.filter() === 'UNREAD'
      ? this.notifications().filter((n) => n.readAt === null)
      : this.notifications()
  );

  /** HELD means no alert channel is stored yet: tell the customer how to release them. */
  readonly heldCount = computed(() => this.notifications().filter((n) => n.status === 'HELD').length);

  /** Seconds until the access token expires, or null when it can't be read. */
  readonly secondsLeft = computed(() => {
    const exp = this.claims()?.exp;
    return exp === undefined ? null : Math.max(0, Math.floor(exp - this.now() / 1000));
  });

  readonly sessionExpired = computed(() => this.secondsLeft() === 0);

  constructor() {
    this.themeService.initialize();
    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    const refresh = setInterval(() => this.load(true), REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(clock);
      clearInterval(refresh);
    });

    this.load();
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

  setFilter(filter: InboxFilter): void {
    this.filter.set(filter);
  }

  refresh(): void {
    this.load();
  }

  /** @param quiet background refresh: keep the current list on screen and do not flash a spinner */
  load(quiet = false): void {
    const claims = this.claims();
    if (!claims || this.sessionExpired()) {
      return;
    }
    if (!quiet) {
      this.loading.set(true);
      this.error.set('');
    }

    this.tradeService.getNotifications(claims.accountId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (notifications) => {
          this.notifications.set(notifications);
          this.countService.set(this.unreadCount());
          this.error.set('');
        },
        error: (error) => {
          if (quiet) {
            return;
          }
          if (error.status === 401 || error.status === 403) {
            this.error.set('Your session is invalid or expired. Please sign in again.');
          } else {
            this.error.set('Unable to load notifications right now.');
          }
        }
      });
  }

  markRead(notification: CustomerNotification): void {
    const claims = this.claims();
    if (!claims || notification.readAt !== null || this.markingRead().has(notification.notificationId)) {
      return;
    }
    this.setMarking(notification.notificationId, true);

    this.tradeService.markNotificationRead(claims.accountId, notification.notificationId)
      .pipe(finalize(() => this.setMarking(notification.notificationId, false)))
      .subscribe({
        next: (updated) => {
          this.notifications.update((list) =>
            list.map((n) => (n.notificationId === updated.notificationId ? updated : n))
          );
          this.countService.set(this.unreadCount());
        },
        error: () => {
          this.error.set('Could not mark that notification as read.');
        }
      });
  }

  markAllRead(): void {
    for (const notification of this.notifications()) {
      if (notification.readAt === null) {
        this.markRead(notification);
      }
    }
  }

  isMarking(notification: CustomerNotification): boolean {
    return this.markingRead().has(notification.notificationId);
  }

  tone(type: NotificationType): 'up' | 'down' | 'muted' | 'accent' {
    switch (type) {
      case 'ORDER_FILLED':
        return 'up';
      case 'ORDER_REJECTED':
        return 'down';
      case 'ORDER_CANCELLED':
        return 'muted';
      default:
        return 'accent';
    }
  }

  /** What happened to the outbound message, in the customer's words. */
  deliveryLabel(notification: CustomerNotification): string {
    switch (notification.status) {
      case 'SENT':
        if (notification.channel === 'BROWSER') {
          return 'Delivered in the app';
        }
        return `Sent by ${CHANNEL_LABELS[notification.channel ?? ''] ?? 'your alert channel'}`;
      case 'FAILED':
        return `Could not be sent by ${CHANNEL_LABELS[notification.channel ?? ''] ?? 'your alert channel'}`;
      case 'SKIPPED':
        return 'Alerts are off, shown here only';
      case 'HELD':
        return 'Waiting for an alert channel';
      default:
        return 'Sending';
    }
  }

  private setMarking(id: number, marking: boolean): void {
    this.markingRead.update((current) => {
      const next = new Set(current);
      if (marking) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
