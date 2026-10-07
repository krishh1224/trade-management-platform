import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PriceAlert, TradeService, WatchlistItem } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';
import { ThemeService } from '../theme/theme.service';
import { AlertsComponent } from './alerts.component';

const ACTIVE_ALERT: PriceAlert = {
  alertId: 1,
  watchlistId: 'w1',
  symbol: 'AAPL',
  instrumentName: 'Apple',
  direction: 'ABOVE',
  threshold: 190,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  triggeredAt: null,
  triggerPrice: null,
  lastPrice: 180,
  currency: 'USD'
};

const TRIGGERED_ALERT: PriceAlert = {
  ...ACTIVE_ALERT,
  alertId: 2,
  watchlistId: 'w2',
  symbol: 'INFY.NS',
  instrumentName: 'Infosys',
  direction: 'BELOW',
  threshold: 1500,
  status: 'TRIGGERED',
  triggeredAt: '2026-01-02T10:15:00Z',
  triggerPrice: 1498.5,
  lastPrice: 1502.35,
  currency: 'INR'
};

function entry(watchlistId: string, symbol: string, group: string, currency: string, lastPrice: number | null): WatchlistItem {
  return {
    watchlistId,
    watchlistGroupId: group,
    watchlistName: group,
    instrumentId: 1,
    symbol,
    instrumentName: symbol === 'AAPL' ? 'Apple' : 'Infosys',
    addedAt: '2026-01-01T00:00:00Z',
    lastPrice,
    changePct: null,
    currency
  };
}

// AAPL sits in two watchlists; an alert is per symbol, so it is offered once
const WATCHED = [
  entry('w1', 'AAPL', 'g1', 'USD', 180),
  entry('w2', 'INFY.NS', 'g1', 'INR', 1502.35),
  entry('w3', 'AAPL', 'g2', 'USD', 180)
];

function alertList(alerts: PriceAlert[] = [ACTIVE_ALERT, TRIGGERED_ALERT], max = 20) {
  return { maxActiveAlerts: max, activeAlerts: alerts.filter((alert) => alert.status === 'ACTIVE').length, alerts };
}

describe('AlertsComponent', () => {
  let component: AlertsComponent;
  let fixture: ComponentFixture<AlertsComponent>;
  let queryParams: Record<string, string>;

  const authSessionMock = {
    claims: signal({ sub: 'alice', accountId: 42, roles: ['USER'], iat: 1, exp: Math.floor(Date.now() / 1000) + 3600 }),
    signOut: vi.fn()
  };

  const tradeServiceMock = {
    listPriceAlerts: vi.fn(),
    getWatchlist: vi.fn(),
    createPriceAlert: vi.fn(),
    deletePriceAlert: vi.fn()
  };

  const theme = signal<'dark' | 'light'>('dark');
  const themeServiceMock = { theme, initialize: vi.fn(), toggleTheme: vi.fn() };
  const routerMock = { navigate: vi.fn().mockResolvedValue(true), navigateByUrl: vi.fn().mockResolvedValue(true) };

  function create(): void {
    fixture = TestBed.createComponent(AlertsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    queryParams = {};
    tradeServiceMock.listPriceAlerts.mockReturnValue(of(alertList()));
    tradeServiceMock.getWatchlist.mockReturnValue(of(WATCHED));
    tradeServiceMock.deletePriceAlert.mockReturnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [AlertsComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthSessionService, useValue: authSessionMock },
        { provide: ThemeService, useValue: themeServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: { snapshot: { get queryParamMap() { return convertToParamMap(queryParams); } } } }
      ]
    }).compileComponents();

    create();
  });

  it('loads alerts with their state and the active-alert limit', () => {
    expect(tradeServiceMock.listPriceAlerts).toHaveBeenCalledWith(42);
    expect(component.priceAlerts()).toHaveLength(2);
    expect(component.activeAlertCount()).toBe(1);

    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('[data-testid="alert-usage"]')?.textContent).toContain('1 of 20 active');
    expect(page.querySelector('tr[data-status="ACTIVE"] .status-badge')?.textContent).toContain('Active');
    expect(page.querySelector('tr[data-status="TRIGGERED"] .status-badge')?.textContent).toContain('Triggered');
  });

  it('shows prices in the currency each instrument is quoted in', () => {
    const page: HTMLElement = fixture.nativeElement;
    const active = page.querySelector('tr[data-status="ACTIVE"]')!.textContent!;
    const triggered = page.querySelector('tr[data-status="TRIGGERED"]')!.textContent!;

    expect(active).toContain('$190.00');
    expect(active).toContain('$180.00');
    expect(triggered).toContain('₹1,500.00');
    expect(triggered).toContain('₹1,502.35');
    expect(triggered).toContain('at ₹1,498.50');
    expect(page.querySelector('[data-testid="current-price"]')?.textContent).toContain('$180.00');
  });

  it('offers each watched symbol once, whichever watchlists hold it', () => {
    expect(tradeServiceMock.getWatchlist).toHaveBeenCalledWith(42);
    expect(component.watchedInstruments().map((item) => item.symbol)).toEqual(['AAPL', 'INFY.NS']);
    expect(component.alertForm.get('symbol')?.value).toBe('AAPL');
  });

  it('preselects the instrument the Watchlist page asked for', () => {
    queryParams = { watchlistId: 'w2' };
    create();

    expect(component.alertForm.get('symbol')?.value).toBe('INFY.NS');
    expect(fixture.nativeElement.querySelector('.price-input-currency').textContent).toContain('₹');
  });

  it('filters by state', () => {
    component.setFilter('TRIGGERED');
    expect(component.visibleAlerts().map((alert) => alert.alertId)).toEqual([2]);

    component.setFilter('ACTIVE');
    expect(component.visibleAlerts().map((alert) => alert.alertId)).toEqual([1]);

    component.setFilter('ALL');
    expect(component.visibleAlerts()).toHaveLength(2);
  });

  it('creates an alert on the chosen instrument', () => {
    tradeServiceMock.createPriceAlert.mockReturnValue(of({ ...TRIGGERED_ALERT, alertId: 3, status: 'ACTIVE', direction: 'ABOVE', threshold: 1600 }));
    component.alertForm.patchValue({ symbol: 'INFY.NS', direction: 'ABOVE', threshold: 1600 });

    component.submitAlertForm();

    expect(tradeServiceMock.createPriceAlert).toHaveBeenCalledWith(42, { watchlistId: 'w2', direction: 'ABOVE', threshold: 1600 });
    expect(component.alertFormSuccess()).toContain('INFY.NS rises to ₹1600');
    expect(tradeServiceMock.listPriceAlerts).toHaveBeenCalledTimes(2);
  });

  it('does not submit an invalid threshold', () => {
    for (const threshold of [null, 0, -5, 1.23456]) {
      component.alertForm.patchValue({ threshold });
      component.submitAlertForm();
    }

    expect(tradeServiceMock.createPriceAlert).not.toHaveBeenCalled();
    expect(component.alertForm.get('threshold')?.touched).toBe(true);
  });

  it('refuses the same active level without calling the API', () => {
    component.alertForm.patchValue({ symbol: 'AAPL', direction: 'ABOVE', threshold: 190.0 });

    component.submitAlertForm();

    expect(tradeServiceMock.createPriceAlert).not.toHaveBeenCalled();
    expect(component.alertFormError()).toContain('already have an active alert on AAPL');
  });

  it('allows the same level again once the earlier alert has triggered, and the other direction', () => {
    tradeServiceMock.createPriceAlert.mockReturnValue(of({ ...ACTIVE_ALERT, alertId: 4 }));

    component.alertForm.patchValue({ symbol: 'INFY.NS', direction: 'BELOW', threshold: 1500 });
    component.submitAlertForm();
    component.alertForm.patchValue({ symbol: 'AAPL', direction: 'BELOW', threshold: 190 });
    component.submitAlertForm();

    expect(tradeServiceMock.createPriceAlert).toHaveBeenCalledTimes(2);
  });

  it('explains the limit (VAL-422) and a duplicate the API caught (WTL-409)', () => {
    component.alertForm.patchValue({ symbol: 'AAPL', direction: 'ABOVE', threshold: 250 });

    tradeServiceMock.createPriceAlert.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 422, error: { errorCode: 'VAL-422', message: 'Price alert limit reached' }
    })));
    component.submitAlertForm();
    expect(component.alertFormError()).toContain('20 active price alerts');

    tradeServiceMock.createPriceAlert.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 409, error: { errorCode: 'WTL-409', message: 'Price alert already exists' }
    })));
    component.submitAlertForm();
    expect(component.alertFormError()).toContain('already have an active alert on AAPL');
  });

  it('uses the platform error catalogue for other failures', () => {
    tradeServiceMock.createPriceAlert.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 403, error: { errorCode: 'ACC-403', message: 'Account not active' }
    })));
    component.alertForm.patchValue({ threshold: 200 });

    component.submitAlertForm();

    expect(component.alertFormError()).toContain('not active for trading, or this session cannot access it');
  });

  it('disables creation at the limit', () => {
    tradeServiceMock.listPriceAlerts.mockReturnValue(of(alertList([ACTIVE_ALERT], 1)));
    create();

    expect(component.atAlertLimit()).toBe(true);
    const submit: HTMLButtonElement = fixture.nativeElement.querySelector('.alert-form button[type="submit"]');
    expect(submit.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Delete one to set another');
  });

  it('deletes an alert and frees an active slot', () => {
    component.deletePriceAlert(ACTIVE_ALERT);

    expect(tradeServiceMock.deletePriceAlert).toHaveBeenCalledWith(42, 1);
    expect(component.priceAlerts().map((alert) => alert.alertId)).toEqual([2]);
    expect(component.activeAlertCount()).toBe(0);
  });

  it('shows a catalogue message when an alert cannot be deleted', () => {
    tradeServiceMock.deletePriceAlert.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 404, error: { errorCode: 'WTL-404', message: 'Price alert not found' }
    })));

    component.deletePriceAlert(ACTIVE_ALERT);

    expect(component.alertsError()).toContain('no longer exists');
    expect(component.priceAlerts()).toHaveLength(2);
  });

  it('points to the Watchlist page when nothing is watched yet', () => {
    tradeServiceMock.getWatchlist.mockReturnValue(of([]));
    create();

    expect(fixture.nativeElement.querySelector('.alert-form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Add an instrument to a watchlist');
  });
});
