import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { TradeService } from '../api/trade.service';
import { HomeComponent } from './home.component';

const authServiceMock = {
  login: vi.fn()
};

const tradeServiceMock = {
  getAccount: vi.fn(),
  getBalance: vi.fn().mockReturnValue(of({ accountId: 1, cashBalance: 0, heldForOrders: 0, availableToWithdraw: 0, currency: 'INR', asOf: '' })),
  getCashTransactions: vi.fn().mockReturnValue(of([])),
  getPositions: vi.fn(),
  getOrders: vi.fn()
};

describe('HomeComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );
    tradeServiceMock.getAccount.mockReset();
    tradeServiceMock.getBalance.mockReset();
    tradeServiceMock.getPositions.mockReset();
    tradeServiceMock.getOrders.mockReset();
    tradeServiceMock.getAccount.mockReturnValue(of({ accountId: 101, holderName: 'Team Five', status: 'ACTIVE' }));
    tradeServiceMock.getBalance.mockReturnValue(
      of({ accountId: 101, cashBalance: 25000, heldForOrders: 0, availableToWithdraw: 25000, currency: 'INR', asOf: '' })
    );
    tradeServiceMock.getPositions.mockReturnValue(of([{ symbol: 'AAPL', quantity: 1, averageCost: 100 }]));
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        { orderId: 'o2', symbol: 'AAPL', side: 'BUY', quantity: 1, price: 101, status: 'FILLED', createdOn: '2026-10-01T00:00:00Z' },
        { orderId: 'o1', symbol: 'MSFT', side: 'BUY', quantity: 1, price: 99, status: 'NEW', createdOn: '2026-10-02T00:00:00Z' }
      ])
    );

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: TradeService, useValue: tradeServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('signing out clears the session', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.signOut();

    expect(localStorage.getItem('trade-ui.auth.session')).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith('/sign-in');
  });

  it('the funds button opens and closes the add / withdraw dialog', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[data-testid="funds-dialog"]')).toBeNull();

    (host.querySelector('[data-testid="funds-open"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="funds-dialog"]')).not.toBeNull();

    (host.querySelector('[data-testid="funds-close"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="funds-dialog"]')).toBeNull();
  });

  it('toggles profile menu from the trigger in the top bar', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[role="menu"]')).toBeNull();
    (host.querySelector('[data-testid="profile-trigger"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('[role="menu"]')).not.toBeNull();

    fixture.componentInstance.closeProfileMenu();
    fixture.detectChanges();
    expect(host.querySelector('[role="menu"]')).toBeNull();
  });

  it('toggles balance visibility and shows formatted amount', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.balanceHidden()).toBe(true);
    component.toggleBalanceVisibility();
    fixture.detectChanges();

    expect(component.balanceHidden()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('INR');
  });

  it('keeps only filled orders in recent orders and maps NEW status label', () => {
    const fixture = TestBed.createComponent(HomeComponent);

    expect(fixture.componentInstance.recentOrders()).toHaveLength(1);
    expect(fixture.componentInstance.recentOrders()[0].status).toBe('FILLED');
    expect(fixture.componentInstance.statusLabel('NEW')).toBe('NEW - Working');
    expect(fixture.componentInstance.statusLabel('FILLED')).toBe('FILLED');
  });
});

function buildToken(): string {
  const header = toBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = toBase64Url(
    JSON.stringify({ sub: 'team5', accountId: 101, roles: ['USER'], iat: 1_700_000_000, exp: 4_102_444_800 })
  );

  return `${header}.${payload}.sig`;
}

function toBase64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}