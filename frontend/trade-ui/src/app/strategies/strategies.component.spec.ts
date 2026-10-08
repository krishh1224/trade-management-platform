import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Strategy, StrategyChart, StrategyExecution, StrategyService } from '../api/strategy.service';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';
import { ThemeService } from '../theme/theme.service';
import { StrategiesComponent } from './strategies.component';

const ENABLED: Strategy = {
  strategyId: 7,
  accountId: 42,
  name: 'INFY trend',
  symbol: 'INFY.NS',
  type: 'MA_CROSSOVER',
  shortWindow: 5,
  longWindow: 20,
  bandWindow: null,
  bandWidth: null,
  quantity: 10,
  maxSpend: 100000,
  spent: 15155.1,
  remainingSpend: 84844.9,
  maxPosition: 50,
  maxConsecutiveFailures: 3,
  consecutiveFailures: 0,
  ordersPlaced: 1,
  status: 'ENABLED',
  statusReason: null,
  lastTriggeredAt: '2026-10-08T09:00:00Z',
  createdAt: '2026-10-08T08:00:00Z',
  updatedAt: '2026-10-08T09:00:00Z'
};

const HALTED: Strategy = {
  ...ENABLED,
  strategyId: 8,
  name: 'AAPL bands',
  symbol: 'AAPL',
  type: 'BOLLINGER_BANDS',
  shortWindow: null,
  longWindow: null,
  bandWindow: 20,
  bandWidth: 2,
  status: 'HALTED',
  statusReason: 'Halted after 3 failed orders in a row. Last: Trade API 403 ACC-403'
};

const EXECUTIONS: StrategyExecution[] = [
  {
    executionId: 2,
    quoteEventId: 'q2',
    signal: 'SELL',
    outcome: 'SKIPPED_DISABLED',
    quotePrice: 1490,
    orderPrice: null,
    quantity: null,
    orderId: null,
    detail: 'Not placed: the strategy was DISABLED when the order was about to be sent.',
    createdAt: '2026-10-08T09:05:00Z'
  },
  {
    executionId: 1,
    quoteEventId: 'q1',
    signal: 'BUY',
    outcome: 'PLACED',
    quotePrice: 1500,
    orderPrice: 1515.51,
    quantity: 10,
    orderId: 'ORD-abc',
    detail: 'BUY 10 INFY.NS at limit 1515.51.',
    createdAt: '2026-10-08T09:00:00Z'
  }
];

const CHART: StrategyChart = {
  strategyId: 7,
  symbol: 'INFY.NS',
  type: 'MA_CROSSOVER',
  requiredPrices: 5,
  points: [1500, 1490, 1480, 1470, 1530].map((price, i) => ({
    quotedAt: `2026-10-08T09:0${i}:00Z`,
    price,
    shortAverage: i >= 1 ? price : null,
    longAverage: i >= 3 ? 1490 : null,
    middleBand: null,
    upperBand: null,
    lowerBand: null
  })),
  markers: [{
    pointIndex: 4, quotedAt: '2026-10-08T09:04:00Z', quotePrice: 1530, signal: 'BUY', outcome: 'PLACED',
    orderId: 'ORD-abc', detail: null
  }]
};

describe('StrategiesComponent', () => {
  let component: StrategiesComponent;
  let fixture: ComponentFixture<StrategiesComponent>;

  const authSessionMock = {
    claims: signal({ sub: 'alice', accountId: 42, roles: ['USER'], iat: 1, exp: Math.floor(Date.now() / 1000) + 3600 }),
    signOut: vi.fn()
  };

  const strategyServiceMock = {
    list: vi.fn(),
    create: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    delete: vi.fn(),
    executions: vi.fn(),
    chart: vi.fn()
  };

  const tradeServiceMock = {
    getInstruments: vi.fn()
  };

  const theme = signal<'dark' | 'light'>('dark');
  const themeServiceMock = { theme, initialize: vi.fn(), toggleTheme: vi.fn() };
  const routerMock = { navigate: vi.fn().mockResolvedValue(true), navigateByUrl: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    strategyServiceMock.list.mockReturnValue(of({ maxStrategies: 10, strategies: [ENABLED, HALTED] }));
    strategyServiceMock.executions.mockReturnValue(of(EXECUTIONS));
    strategyServiceMock.chart.mockReturnValue(of(CHART));
    tradeServiceMock.getInstruments.mockReturnValue(of([
      { symbol: 'INFY.NS', name: 'Infosys', currency: 'INR' },
      { symbol: 'AAPL', name: 'Apple', currency: 'USD' }
    ]));

    await TestBed.configureTestingModule({
      imports: [StrategiesComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StrategyService, useValue: strategyServiceMock },
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthSessionService, useValue: authSessionMock },
        { provide: ThemeService, useValue: themeServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StrategiesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists the account\'s strategies with their status, and why a halted one stopped', () => {
    expect(strategyServiceMock.list).toHaveBeenCalledWith(42);
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('[data-testid="strategy-usage"]')?.textContent).toContain('2 of 10');
    expect(page.querySelector('tr[data-status="ENABLED"]')?.textContent).toContain('MA crossover 5 / 20');
    expect(page.querySelector('tr[data-status="ENABLED"]')?.textContent).toContain('₹15,155.1');
    const halted = page.querySelector('tr[data-status="HALTED"]')!.textContent!;
    expect(halted).toContain('Bollinger 20, 2 sd');
    expect(halted).toContain('Halted after 3 failed orders in a row');
  });

  it('shows the first strategy\'s activity, including what a disable stopped', () => {
    expect(strategyServiceMock.executions).toHaveBeenCalledWith(42, 7);
    const activity: HTMLElement = fixture.nativeElement.querySelector('[data-testid="strategy-activity"]');
    expect(activity.querySelector('tr[data-outcome="PLACED"]')?.textContent).toContain('ORD-abc');
    expect(activity.querySelector('tr[data-outcome="SKIPPED_DISABLED"]')?.textContent).toContain('Not placed: disabled');
  });

  it('draws the selected strategy\'s chart with its marker', () => {
    expect(strategyServiceMock.chart).toHaveBeenCalledWith(42, 7);
    const panel: HTMLElement = fixture.nativeElement.querySelector('[data-testid="strategy-chart-panel"]');
    expect(panel.querySelector('[data-testid="strategy-chart"]')).not.toBeNull();
    expect(panel.querySelector('[data-testid="chart-waiting"]')).toBeNull();
    expect(panel.querySelectorAll('.marker')).toHaveLength(1);
  });

  it('says how many prices are still needed before the rule can fire', () => {
    strategyServiceMock.chart.mockReturnValue(of({ ...CHART, points: CHART.points.slice(0, 3), markers: [] }));
    component.select(HALTED);
    fixture.detectChanges();

    const waiting = fixture.nativeElement.querySelector('[data-testid="chart-waiting"]');
    expect(waiting?.textContent).toContain('3 of 5 prices');
  });

  it('disables a strategy and shows it disabled', () => {
    strategyServiceMock.disable.mockReturnValue(of({ ...ENABLED, status: 'DISABLED', statusReason: 'Disabled by the customer' }));

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('tr[data-status="ENABLED"] .disable-button');
    button.click();
    fixture.detectChanges();

    expect(strategyServiceMock.disable).toHaveBeenCalledWith(42, 7);
    expect(component.strategies().find((s) => s.strategyId === 7)?.status).toBe('DISABLED');
    expect(fixture.nativeElement.querySelector('tr[data-status="DISABLED"]')?.textContent).toContain('Disabled by the customer');
  });

  it('re-enables a halted strategy', () => {
    strategyServiceMock.enable.mockReturnValue(of({ ...HALTED, status: 'ENABLED', statusReason: null }));

    component.enable(HALTED);

    expect(strategyServiceMock.enable).toHaveBeenCalledWith(42, 8);
    expect(component.strategies().find((s) => s.strategyId === 8)?.status).toBe('ENABLED');
  });

  it('creates a crossover strategy with its windows and limits only', () => {
    strategyServiceMock.create.mockReturnValue(of({ ...ENABLED, strategyId: 9, name: 'New one' }));
    component.strategyForm.patchValue({ name: 'New one', symbol: 'INFY.NS', maxSpend: 50000, maxPosition: 20 });

    component.submit();

    expect(strategyServiceMock.create).toHaveBeenCalledWith(42, {
      name: 'New one',
      symbol: 'INFY.NS',
      type: 'MA_CROSSOVER',
      shortWindow: 5,
      longWindow: 20,
      quantity: 1,
      maxSpend: 50000,
      maxPosition: 20,
      maxConsecutiveFailures: 3
    });
    expect(component.formSuccess()).toContain('New one is on');
  });

  it('creates a Bollinger strategy without crossover windows', () => {
    strategyServiceMock.create.mockReturnValue(of(HALTED));
    component.strategyForm.patchValue({ name: 'Bands', symbol: 'AAPL', type: 'BOLLINGER_BANDS', maxSpend: 1000, maxPosition: 5 });

    component.submit();

    const body = strategyServiceMock.create.mock.calls[0][1];
    expect(body.bandWindow).toBe(20);
    expect(body.bandWidth).toBe(2);
    expect(body.shortWindow).toBeUndefined();
  });

  it('refuses a position limit smaller than one order without calling the API', () => {
    component.strategyForm.patchValue({ name: 'Too small', quantity: 10, maxSpend: 1000, maxPosition: 5 });

    component.submit();

    expect(strategyServiceMock.create).not.toHaveBeenCalled();
    expect(component.formError()).toContain('at least the order quantity');
  });

  it('says which field is wrong instead of a generic message', () => {
    component.strategyForm.patchValue({ name: 'demo', shortWindow: 1, longWindow: 1, quantity: 1, maxSpend: 1500, maxPosition: 10 });

    component.submit();

    expect(strategyServiceMock.create).not.toHaveBeenCalled();
    expect(component.formError()).toBe('Short window must be between 2 and 199. Long window must be between 3 and 200.');
  });

  it('refuses a short window that is not smaller than the long one', () => {
    component.strategyForm.patchValue({ name: 'demo', shortWindow: 5, longWindow: 5, maxSpend: 1500, maxPosition: 10 });

    component.submit();

    expect(strategyServiceMock.create).not.toHaveBeenCalled();
    expect(component.formError()).toBe('Short window must be smaller than long window.');
  });

  it('shows the server\'s reason when it refuses the strategy', () => {
    strategyServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { errorCode: 'VAL-422', message: 'Strategy limit reached' }
    })));
    component.strategyForm.patchValue({ name: 'One more', maxSpend: 1000, maxPosition: 5 });

    component.submit();

    expect(component.formError()).toBe('Strategy limit reached');
  });
});
