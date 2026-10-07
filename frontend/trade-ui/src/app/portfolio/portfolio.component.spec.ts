import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { Candle, CandlesResponse, PnlResponse, PortfolioService, PortfolioSummary, PricedPosition } from '../api/portfolio.service';
import { TradeService } from '../api/trade.service';
import { PortfolioComponent } from './portfolio.component';

const portfolioServiceMock = {
  getSummary: vi.fn(),
  getPositions: vi.fn(),
  getCandles: vi.fn(),
  getPnl: vi.fn()
};

const tradeServiceMock = {
  getPositions: vi.fn(),
  getBalance: vi.fn()
};

describe('PortfolioComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    portfolioServiceMock.getSummary.mockReset().mockReturnValue(of(summary()));
    portfolioServiceMock.getPositions.mockReset().mockReturnValue(of([position()]));
    portfolioServiceMock.getCandles.mockReset().mockReturnValue(of(candles()));
    portfolioServiceMock.getPnl.mockReset().mockReturnValue(of(pnl()));
    tradeServiceMock.getPositions.mockReset().mockReturnValue(of([]));
    tradeServiceMock.getBalance.mockReset().mockReturnValue(of({ cashBalance: 5000, currency: 'INR' }));

    await TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login: vi.fn() } },
        { provide: PortfolioService, useValue: portfolioServiceMock },
        { provide: TradeService, useValue: tradeServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  function render() {
    const fixture = TestBed.createComponent(PortfolioComponent);
    fixture.detectChanges();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  const text = (host: HTMLElement, testId: string) => host.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();

  it('loads the summary for the signed-in account, then its priced positions', () => {
    render();

    expect(portfolioServiceMock.getSummary).toHaveBeenCalledWith(101);
    expect(portfolioServiceMock.getPositions).toHaveBeenCalledWith(101);
  });

  it('shows the totals in the base currency and a live state when every price is fresh', () => {
    const { host } = render();

    expect(text(host, 'total-value')).toBe('INR 920,315.17');
    expect(text(host, 'market-value')).toBe('INR 552,416.87');
    expect(text(host, 'unrealised-pnl')).toBe('INR +18,110.11');
    expect(text(host, 'realised-pnl')).toBe('INR +1,145.50');
    expect(text(host, 'cash')).toBe('INR 367,898.30');
    expect(text(host, 'state-pill')).toContain('Live');
    expect(host.querySelector('[data-testid="stale-chip"]')).toBeNull();
    expect(host.querySelector('.banner')).toBeNull();
  });

  it('renders a priced position with its price, value and profit or loss', () => {
    const { host } = render();

    const row = host.querySelector('[data-testid="row-AAPL"]') as HTMLElement;
    expect(row.textContent).toContain('USD');
    expect(row.textContent).toContain('232.71');
    expect(row.textContent).toContain('46,542.00');
    expect(row.textContent).toContain('+862.00');
    expect(row.textContent).toContain('+1.89%');
  });

  it('marks a stale quote as delayed and keeps showing the number', () => {
    portfolioServiceMock.getPositions.mockReturnValue(of([position({ stale: true })]));

    const { host } = render();

    expect(host.querySelector('[data-testid="stale-chip"]')?.textContent).toContain('Delayed');
    expect(host.querySelector('[data-testid="row-AAPL"]')?.textContent).toContain('232.71');
    expect(text(host, 'state-pill')).toContain('Delayed quotes');
    expect(host.querySelector('[data-testid="delayed-banner"]')).not.toBeNull();
  });

  it('flags a holding that could not be priced and says the totals leave it out', () => {
    portfolioServiceMock.getSummary.mockReturnValue(of(summary({ partial: true })));
    portfolioServiceMock.getPositions.mockReturnValue(
      of([
        position(),
        position({
          symbol: 'INFY.NS',
          currency: 'INR',
          lastPrice: null,
          marketValue: null,
          unrealisedPnl: null,
          unrealisedPnlPercent: null,
          priceAsOf: null,
          stale: true
        })
      ])
    );

    const { host } = render();

    expect(host.querySelector('[data-testid="partial-banner"]')?.textContent).toContain('One holding');
    expect(host.querySelector('[data-testid="row-INFY.NS"] [data-testid="unpriced-chip"]')).not.toBeNull();
    expect(text(host, 'state-pill')).toContain('Partly priced');
  });

  it('falls back to holdings at cost when pricing is unavailable (MKT-503)', () => {
    portfolioServiceMock.getSummary.mockReturnValue(throwError(() => pricingUnavailable()));
    tradeServiceMock.getPositions.mockReturnValue(
      of([{ accountId: 101, symbol: 'INFY.NS', quantity: 40, averageCost: 1580.25 }])
    );

    const { host } = render();

    expect(host.querySelector('[data-testid="pricing-down-banner"]')).not.toBeNull();
    expect(portfolioServiceMock.getPositions).not.toHaveBeenCalled();
    expect(tradeServiceMock.getPositions).toHaveBeenCalledWith(101);
    const row = host.querySelector('[data-testid="row-INFY.NS"]') as HTMLElement;
    expect(row.textContent).toContain('63,210.00');
    expect(row.textContent).toContain('INR');
    expect(row.querySelector('[data-testid="unpriced-chip"]')).not.toBeNull();
    expect(text(host, 'cash')).toBe('INR 5,000.00');
    expect(text(host, 'state-pill')).toContain('Prices unavailable');
    expect(host.querySelector('[data-testid="portfolio-error"]')).toBeNull();
  });

  it('shows an error with a retry when the portfolio cannot be loaded for another reason', () => {
    portfolioServiceMock.getSummary.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { errorCode: 'UNK-1' } }))
    );

    const { host } = render();

    expect(host.querySelector('[data-testid="portfolio-error"]')?.textContent).toContain('could not be loaded');
    expect(host.querySelector('[data-testid="pricing-down-banner"]')).toBeNull();
  });

  it('says so when the account holds nothing', () => {
    portfolioServiceMock.getSummary.mockReturnValue(
      of(summary({ marketValue: 0, costBasis: 0, unrealisedPnl: 0, unrealisedPnlPercent: null, positionCount: 0 }))
    );
    portfolioServiceMock.getPositions.mockReturnValue(of([]));

    const { host } = render();

    expect(host.textContent).toContain('No open positions');
  });

  it('refreshes on demand', () => {
    const { fixture, host } = render();

    (host.querySelector('[data-testid="refresh"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(portfolioServiceMock.getSummary).toHaveBeenCalledTimes(2);
  });

  it('masks the summary amounts when they are hidden', () => {
    const { fixture, host } = render();

    (host.querySelector('[data-testid="toggle-values"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(text(host, 'total-value')).toBe('••••••••');
    expect(text(host, 'unrealised-pnl')).toBe('••••••••');
  });

  it('keeps the Portfolio link highlighted as the current page', () => {
    const { host } = render();

    const active = host.querySelector('.nav-link.active') as HTMLElement;
    expect(active.textContent).toContain('Portfolio');
    expect(active.getAttribute('aria-current')).toBe('page');
  });

  describe('profit and loss', () => {
    it('asks for the all-time breakdown once the holdings are in', () => {
      render();

      expect(portfolioServiceMock.getPnl).toHaveBeenCalledWith(101, { from: undefined, bySymbol: true });
    });

    it('lists realised, unrealised and total per instrument with a total row', () => {
      const { host } = render();

      const aapl = host.querySelector('[data-testid="pnl-row-AAPL"]') as HTMLElement;
      expect(aapl.textContent).toContain('+1,145.50');
      expect(aapl.textContent).toContain('+862.00');
      expect(aapl.textContent).toContain('+2,007.50');
      const total = host.querySelector('[data-testid="pnl-total"]') as HTMLElement;
      expect(total.textContent).toContain('+1,145.50');
      expect(total.textContent).toContain('+18,110.11');
      expect(total.textContent).toContain('+19,255.61');
    });

    it('colours losses and shows them with their sign', () => {
      portfolioServiceMock.getPnl.mockReturnValue(of(pnl({
        bySymbol: [{ symbol: 'ITC.NS', realisedPnl: 0, unrealisedPnl: -24495, totalPnl: -24495 }]
      })));

      const { host } = render();

      const loss = host.querySelector('[data-testid="pnl-row-ITC.NS"] .pnl.down') as HTMLElement;
      expect(loss.textContent?.trim()).toBe('-24,495.00');
    });

    it('re-asks with a start date when a period is picked and keeps the same unrealised basis', () => {
      const { fixture, host } = render();

      (host.querySelector('[data-testid="pnl-period-30D"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      const [, options] = portfolioServiceMock.getPnl.mock.calls[1];
      expect(options.bySymbol).toBe(true);
      expect(options.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(portfolioServiceMock.getSummary).toHaveBeenCalledTimes(1);
      expect(host.querySelector('[data-testid="pnl-period-30D"]')?.getAttribute('aria-selected')).toBe('true');
    });

    it('says so when there is nothing to report', () => {
      portfolioServiceMock.getPnl.mockReturnValue(of(pnl({ bySymbol: [], realisedPnl: 0, unrealisedPnl: 0, totalPnl: 0 })));

      const { host } = render();

      expect(host.querySelector('[data-testid="pnl-empty"]')).not.toBeNull();
      expect(host.querySelector('[data-testid="pnl-table"]')).toBeNull();
    });

    it('fails on its own without taking the rest of the page with it', () => {
      portfolioServiceMock.getPnl.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: {} })));

      const { host } = render();

      expect(host.querySelector('[data-testid="pnl-error"]')?.textContent).toContain('could not be loaded');
      expect(host.querySelector('[data-testid="row-AAPL"]')).not.toBeNull();
      expect(text(host, 'total-value')).toBe('INR 920,315.17');
    });

    it('is unavailable, not blank, when prices are down', () => {
      portfolioServiceMock.getSummary.mockReturnValue(throwError(() => pricingUnavailable()));
      tradeServiceMock.getPositions.mockReturnValue(of([]));

      const { host } = render();

      expect(portfolioServiceMock.getPnl).not.toHaveBeenCalled();
      expect(host.querySelector('[data-testid="pnl-error"]')?.textContent).toContain('needs live prices');
      expect((host.querySelector('[data-testid="pnl-period-30D"]') as HTMLButtonElement).disabled).toBe(true);
    });

    it('masks the amounts when they are hidden', () => {
      const { fixture, host } = render();

      (host.querySelector('[data-testid="toggle-values"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(host.querySelector('[data-testid="pnl-total"]')?.textContent).not.toContain('19,255.61');
      expect(host.querySelector('[data-testid="pnl-total"]')?.textContent).toContain('••••••••');
    });
  });

  describe('price history', () => {
    const open = (fixture: ComponentFixture<PortfolioComponent>, symbol = 'AAPL') => {
      (fixture.nativeElement.querySelector(`[data-testid="row-${symbol}"]`) as HTMLElement).click();
      fixture.detectChanges();
    };

    it('opens a candlestick chart under the holding that was clicked', () => {
      const { fixture, host } = render();
      expect(host.querySelector('[data-testid="history-AAPL"]')).toBeNull();

      open(fixture);

      expect(portfolioServiceMock.getCandles).toHaveBeenCalledWith(101, 'AAPL', '3M');
      expect(host.querySelector('[data-testid="history-AAPL"] app-candle-chart')).not.toBeNull();
      expect(host.querySelectorAll('g.candle')).toHaveLength(3);
      expect(host.querySelector('[data-testid="row-toggle"]')?.getAttribute('aria-expanded')).toBe('true');
    });

    it('summarises the range: last close, change, high and low', () => {
      const { fixture, host } = render();

      open(fixture);

      const history = host.querySelector('[data-testid="history-AAPL"]') as HTMLElement;
      expect(history.textContent).toContain('Change over 3M');
      expect(history.textContent).toContain('+10.00%');
      expect(history.textContent).toContain('Period high');
      expect(history.textContent).toContain('121.00');
      expect(history.textContent).toContain('Period low');
      expect(history.textContent).toContain('88.00');
    });

    it('collapses when the row is clicked again', () => {
      const { fixture, host } = render();

      open(fixture);
      open(fixture);

      expect(host.querySelector('[data-testid="history-AAPL"]')).toBeNull();
    });

    it('requests another range once and reuses what it already loaded', () => {
      const { fixture, host } = render();
      open(fixture);

      (host.querySelector('[data-testid="range-1Y"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(portfolioServiceMock.getCandles).toHaveBeenLastCalledWith(101, 'AAPL', '1Y');

      (host.querySelector('[data-testid="range-3M"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(portfolioServiceMock.getCandles).toHaveBeenCalledTimes(2);
    });

    it('explains a failure and offers a retry', () => {
      portfolioServiceMock.getCandles.mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503' } }))
      );
      const { fixture, host } = render();

      open(fixture);

      expect(host.querySelector('[data-testid="candles-error"]')?.textContent).toContain('Price history is unavailable');
      (host.querySelector('[data-testid="candles-error"] button') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(host.querySelector('[data-testid="candles-error"]')).toBeNull();
      expect(host.querySelectorAll('g.candle')).toHaveLength(3);
    });

    it('says when the history is a saved copy because the feed is down', () => {
      portfolioServiceMock.getCandles.mockReturnValue(of(candles({ stale: true })));
      const { fixture, host } = render();

      open(fixture);

      expect(host.querySelector('[data-testid="candles-stale"]')?.textContent).toContain('saved copy');
    });

    it('says so when a range has no bars', () => {
      portfolioServiceMock.getCandles.mockReturnValue(of(candles({ candles: [] })));
      const { fixture, host } = render();

      open(fixture);

      expect(host.querySelector('[data-testid="candles-empty"]')).not.toBeNull();
    });
  });
});

function pnl(overrides: Partial<PnlResponse> = {}): PnlResponse {
  return {
    accountId: 101,
    baseCurrency: 'INR',
    from: null,
    to: null,
    realisedPnl: 1145.5,
    unrealisedPnl: 18110.11,
    totalPnl: 19255.61,
    bySymbol: [{ symbol: 'AAPL', realisedPnl: 1145.5, unrealisedPnl: 862, totalPnl: 2007.5 }],
    asOf: '2026-10-06T15:16:19Z',
    ...overrides
  };
}

function candles(overrides: Partial<CandlesResponse> = {}): CandlesResponse {
  const bar = (date: string, close: number, high: number, low: number): Candle => ({
    date, open: 100, high, low, close, volume: 1000, synthetic: false
  });
  return {
    symbol: 'AAPL',
    currency: 'USD',
    interval: '1d',
    range: '3M',
    stale: false,
    asOf: '2026-10-06T15:00:00Z',
    candles: [bar('2026-10-01', 100, 105, 88), bar('2026-10-02', 104, 121, 99), bar('2026-10-05', 110, 112, 101)],
    ...overrides
  };
}

function pricingUnavailable(): HttpErrorResponse {
  return new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503', message: 'Pricing unavailable' } });
}

function summary(overrides: Partial<PortfolioSummary> = {}): PortfolioSummary {
  return {
    accountId: 101,
    baseCurrency: 'INR',
    cashBalance: 367898.3,
    marketValue: 552416.87,
    costBasis: 534306.76,
    unrealisedPnl: 18110.11,
    unrealisedPnlPercent: 3.39,
    realisedPnl: 1145.5,
    totalValue: 920315.17,
    positionCount: 1,
    partial: false,
    asOf: '2026-10-06T15:16:19Z',
    ...overrides
  };
}

function position(overrides: Partial<PricedPosition> = {}): PricedPosition {
  return {
    accountId: 101,
    symbol: 'AAPL',
    quantity: 200,
    averageCost: 228.4,
    costBasis: 45680,
    lastPrice: 232.71,
    marketValue: 46542,
    unrealisedPnl: 862,
    unrealisedPnlPercent: 1.89,
    currency: 'USD',
    priceAsOf: '2026-10-06T15:03:06Z',
    stale: false,
    ...overrides
  };
}

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
