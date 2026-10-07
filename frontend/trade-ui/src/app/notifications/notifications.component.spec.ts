import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CustomerNotification, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { NotificationCountService } from './notification-count.service';
import { NotificationsComponent } from './notifications.component';

function notification(overrides: Partial<CustomerNotification>): CustomerNotification {
  return {
    notificationId: 1,
    type: 'ORDER_FILLED',
    orderId: 9001,
    title: 'Order filled',
    message: 'Your BUY order for 10 AAPL was filled at 187.25.',
    status: 'SENT',
    channel: 'EMAIL',
    createdAt: '2026-10-07T09:00:00Z',
    sentAt: '2026-10-07T09:00:05Z',
    readAt: null,
    ...overrides
  };
}

describe('NotificationsComponent', () => {
  let component: NotificationsComponent;
  let fixture: ComponentFixture<NotificationsComponent>;

  const filled = notification({ notificationId: 1 });
  const rejected = notification({
    notificationId: 2,
    type: 'ORDER_REJECTED',
    title: 'Order rejected',
    message: 'Your SELL order for 5 TSLA was rejected (insufficient holdings).',
    readAt: '2026-10-07T09:10:00Z'
  });

  const authSessionMock = {
    claims: signal({
      sub: 'alice',
      accountId: 42,
      roles: ['USER'],
      iat: 1,
      exp: Math.floor(Date.now() / 1000) + 3600
    }),
    signOut: vi.fn()
  };

  const tradeServiceMock = {
    getNotifications: vi.fn(),
    markNotificationRead: vi.fn()
  };

  const countServiceMock = {
    unreadCount: signal(0),
    refresh: vi.fn(),
    set: vi.fn()
  };

  const theme = signal<'dark' | 'light'>('dark');
  const themeServiceMock = {
    theme,
    initialize: vi.fn(),
    toggleTheme: vi.fn()
  };

  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthSessionService, useValue: authSessionMock },
        { provide: ThemeService, useValue: themeServiceMock },
        { provide: NotificationCountService, useValue: countServiceMock }
      ]
    }).compileComponents();

    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    tradeServiceMock.getNotifications.mockReturnValue(of([filled, rejected]));
    tradeServiceMock.markNotificationRead.mockImplementation((_accountId: number, id: number) =>
      of({ ...(id === 1 ? filled : rejected), readAt: '2026-10-07T09:30:00Z' })
    );
  });

  it('loads the signed-in account’s notifications', async () => {
    const element = await render();

    expect(tradeServiceMock.getNotifications).toHaveBeenCalledWith(42);
    expect(element.textContent).toContain('Your BUY order for 10 AAPL was filled at 187.25.');
    expect(element.textContent).toContain('Your SELL order for 5 TSLA was rejected (insufficient holdings).');
  });

  it('shows rejections as well as fills, with where each one was delivered', async () => {
    const element = await render();

    const items = element.querySelectorAll('.notification');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Sent by email');
    expect(items[1].querySelector('.dot')?.classList).toContain('down');
  });

  it('marks only unread notifications and counts them', async () => {
    const element = await render();

    expect(component.unreadCount()).toBe(1);
    expect(element.querySelector('[data-testid="notification-1"]')?.classList).toContain('unread');
    expect(element.querySelector('[data-testid="notification-2"]')?.classList).not.toContain('unread');
  });

  it('filters to unread and shows an empty state once caught up', async () => {
    const element = await render();

    component.setFilter('UNREAD');
    fixture.detectChanges();
    expect(element.querySelectorAll('.notification')).toHaveLength(1);

    component.markRead(filled);
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="empty-state"]')?.textContent).toContain('all caught up');
  });

  it('marks a notification read through the API', async () => {
    await render();

    component.markRead(filled);

    expect(tradeServiceMock.markNotificationRead).toHaveBeenCalledWith(42, 1);
    expect(component.notifications().find((n) => n.notificationId === 1)?.readAt).toBe('2026-10-07T09:30:00Z');
    expect(component.unreadCount()).toBe(0);
  });

  it('keeps the header bell count in step with the inbox', async () => {
    await render();
    expect(countServiceMock.set).toHaveBeenLastCalledWith(1);

    component.markRead(filled);

    expect(countServiceMock.set).toHaveBeenLastCalledWith(0);
  });

  it('marks the bell as the current page', async () => {
    const element = await render();

    expect(element.querySelector('[data-testid="notification-bell"]')?.getAttribute('aria-current')).toBe('page');
    expect(element.querySelector('nav')?.textContent).not.toContain('Notifications');
  });

  it('does not call the API for a notification that is already read', async () => {
    await render();

    component.markRead(rejected);

    expect(tradeServiceMock.markNotificationRead).not.toHaveBeenCalled();
  });

  it('mark all read only touches unread notifications', async () => {
    await render();

    component.markAllRead();

    expect(tradeServiceMock.markNotificationRead).toHaveBeenCalledTimes(1);
    expect(tradeServiceMock.markNotificationRead).toHaveBeenCalledWith(42, 1);
  });

  it('explains held notifications and links to the alert channel setting', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(
      of([notification({ notificationId: 3, status: 'HELD', channel: null, sentAt: null })])
    );

    const element = await render();

    const banner = element.querySelector('[data-testid="held-banner"]');
    expect(banner?.textContent).toContain('1 notification is waiting');
    expect(banner?.querySelector('a')?.getAttribute('href')).toBe('/profile');
    expect(element.textContent).toContain('Waiting for an alert channel');
  });

  it('describes each delivery outcome', async () => {
    await render();

    expect(component.deliveryLabel(notification({ status: 'SENT', channel: 'SMS' }))).toBe('Sent by SMS');
    expect(component.deliveryLabel(notification({ status: 'SENT', channel: 'PUSH' }))).toBe('Sent by push notification');
    expect(component.deliveryLabel(notification({ status: 'SENT', channel: 'BROWSER' }))).toBe('Delivered in the app');
    expect(component.deliveryLabel(notification({ status: 'FAILED', channel: 'EMAIL' }))).toBe('Could not be sent by email');
    expect(component.deliveryLabel(notification({ status: 'SKIPPED', channel: 'NONE' }))).toBe('Alerts are off, shown here only');
    expect(component.deliveryLabel(notification({ status: 'QUEUED', channel: null }))).toBe('Sending');
  });

  it('shows a session message when the API refuses the request', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(throwError(() => ({ status: 403 })));

    const element = await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('session is invalid or expired');
  });

  it('signs out and returns to sign-in', async () => {
    await render();

    component.signOut();

    expect(authSessionMock.signOut).toHaveBeenCalled();
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/sign-in');
  });

  it('shows a generic message when the API fails', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(throwError(() => ({ status: 500 })));

    const element = await render();

    expect(element.textContent).toContain('Unable to load notifications right now.');
  });
});
