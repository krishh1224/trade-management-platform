import { Component, DestroyRef, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthSessionService } from '../auth/auth-session.service';
import { NotificationCountService } from './notification-count.service';

const REFRESH_INTERVAL_MS = 30_000;

/** Bell in the top bar: opens the inbox and shows how many notifications are unread. */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink],
  template: `
    <a
      class="bell"
      [class.active]="active()"
      routerLink="/notifications"
      [attr.aria-label]="label()"
      [attr.aria-current]="active() ? 'page' : null"
      [attr.title]="label()"
      data-testid="notification-bell"
    >
      <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
        <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
      @if (count() > 0) {
        <span class="badge" data-testid="notification-badge">{{ count() > 99 ? '99+' : count() }}</span>
      }
    </a>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .bell {
      position: relative;
      height: 38px;
      width: 38px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      background: var(--surface-2);
      color: var(--text-muted);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      text-decoration: none;
    }

    .bell:hover,
    .bell.active {
      border-color: var(--accent);
      background: var(--accent-soft);
      color: var(--accent-hover);
    }

    .badge {
      position: absolute;
      top: -6px;
      right: -6px;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      border: 2px solid var(--bar-bg, var(--surface));
      border-radius: 999px;
      background: var(--down);
      color: #fff;
      font-size: 0.66rem;
      font-weight: 700;
      line-height: 14px;
      text-align: center;
      font-variant-numeric: tabular-nums;
    }
  `
})
export class NotificationBellComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly countService = inject(NotificationCountService);

  /** True on the inbox page itself. */
  readonly active = input(false);

  readonly count = this.countService.unreadCount;

  readonly label = computed(() => {
    const count = this.count();
    if (count === 0) {
      return 'Notifications';
    }
    return `Notifications, ${count} unread`;
  });

  constructor() {
    this.refresh();
    const timer = setInterval(() => this.refresh(), REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    const claims = this.authSession.claims();
    if (claims && claims.exp > Date.now() / 1000) {
      this.countService.refresh(claims.accountId);
    }
  }
}
