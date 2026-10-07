import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CustomerNotification, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationCountService } from './notification-count.service';

function notification(id: number, readAt: string | null): CustomerNotification {
  return {
    notificationId: id,
    type: 'ORDER_FILLED',
    orderId: id,
    title: 'Order filled',
    message: 'Your BUY order for 10 AAPL was filled at 187.25.',
    status: 'SENT',
    channel: 'EMAIL',
    createdAt: '2026-10-07T09:00:00Z',
    sentAt: '2026-10-07T09:00:05Z',
    readAt
  };
}

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;

  const claims = signal<{ sub: string; accountId: number; roles: string[]; iat: number; exp: number } | null>(null);
  const tradeServiceMock = { getNotifications: vi.fn() };

  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [
        provideRouter([]),
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthSessionService, useValue: { claims } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    claims.set({ sub: 'alice', accountId: 42, roles: ['USER'], iat: 1, exp: Math.floor(Date.now() / 1000) + 3600 });
    tradeServiceMock.getNotifications.mockReturnValue(
      of([notification(1, null), notification(2, null), notification(3, '2026-10-07T09:10:00Z')])
    );
  });

  it('shows the unread count for the signed-in account and links to the inbox', async () => {
    const element = await render();

    expect(tradeServiceMock.getNotifications).toHaveBeenCalledWith(42, undefined, 100);
    expect(element.querySelector('[data-testid="notification-badge"]')?.textContent?.trim()).toBe('2');
    const bell = element.querySelector('[data-testid="notification-bell"]');
    expect(bell?.getAttribute('href')).toBe('/notifications');
    expect(bell?.getAttribute('aria-label')).toBe('Notifications, 2 unread');
  });

  it('hides the badge when everything is read', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(of([notification(1, '2026-10-07T09:10:00Z')]));

    const element = await render();

    expect(element.querySelector('[data-testid="notification-badge"]')).toBeNull();
    expect(element.querySelector('[data-testid="notification-bell"]')?.getAttribute('aria-label')).toBe('Notifications');
  });

  it('caps a large count at 99+', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(
      of(Array.from({ length: 100 }, (_, i) => notification(i + 1, null)))
    );

    const element = await render();

    expect(element.querySelector('[data-testid="notification-badge"]')?.textContent?.trim()).toBe('99+');
  });

  it('follows a count changed by the inbox page', async () => {
    const element = await render();

    TestBed.inject(NotificationCountService).set(0);
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="notification-badge"]')).toBeNull();
  });

  it('does not call the API once the session has expired', async () => {
    claims.set({ sub: 'alice', accountId: 42, roles: ['USER'], iat: 1, exp: 1 });

    await render();

    expect(tradeServiceMock.getNotifications).not.toHaveBeenCalled();
  });

  it('keeps quiet when the API fails', async () => {
    tradeServiceMock.getNotifications.mockReturnValue(throwError(() => ({ status: 500 })));

    const element = await render();

    expect(element.querySelector('[data-testid="notification-badge"]')).toBeNull();
    expect(element.querySelector('[data-testid="notification-bell"]')).not.toBeNull();
  });
});
