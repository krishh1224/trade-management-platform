import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { TradeService } from '../api/trade.service';
import { AdvisorComponent } from './advisor.component';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';

const tradeServiceMock = {
  getAdviceSignals: vi.fn()
};

describe('AdvisorComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );
    tradeServiceMock.getAdviceSignals.mockReset();
    tradeServiceMock.getAdviceSignals.mockReturnValue(
      of({ accountId: 103, generatedAt: '2026-10-07T12:30:00Z', signals: [], skipped: [] })
    );

    await TestBed.configureTestingModule({
      imports: [AdvisorComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        { provide: TradeService, useValue: tradeServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({}) },
            queryParamMap: of(convertToParamMap({}))
          }
        }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('shows the disclaimer and empty state before any signals arrive', () => {
    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('not investment advice');
    expect(text).toContain('No signals are available yet');
  });

  it('loads signals from the new route and renders reasoning', () => {
    tradeServiceMock.getAdviceSignals.mockReturnValue(
      of({
        accountId: 103,
        generatedAt: '2026-10-07T12:30:00Z',
        signals: [
          {
            symbol: 'ITC.NS',
            instrumentName: null,
            origin: 'HOLDING',
            signal: 'BUY',
            reasoning: [
              'Unrealised P&L is +14.00% versus average cost.',
              'The signal uses the latest stored holding price from the platform.'
            ],
            lastPrice: 505.45,
            changePct: null,
            unrealisedPnlPercent: 14,
            currency: 'INR',
            priceAsOf: '2026-10-07T12:25:00Z',
            stale: false
          }
        ],
        skipped: []
      })
    );

    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    expect(tradeServiceMock.getAdviceSignals).toHaveBeenCalledWith(103);
    const card = fixture.nativeElement.querySelector('[data-testid="advisor-signal-card"]') as HTMLElement;
    expect(card.textContent).toContain('ITC.NS');
    expect(card.textContent).toContain('BUY');
    expect(card.textContent).toContain('Unrealised P&L is +14.00% versus average cost.');
  });

  it('shows skipped symbols when data is insufficient', () => {
    tradeServiceMock.getAdviceSignals.mockReturnValue(
      of({
        accountId: 103,
        generatedAt: '2026-10-07T12:30:00Z',
        signals: [],
        skipped: [
          {
            symbol: 'TCS.NS',
            instrumentName: 'Tata Consultancy Services',
            origin: 'WATCHLIST',
            reason: 'The watchlist entry has no stored market move yet.'
          }
        ]
      })
    );
    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    const skipped = fixture.nativeElement.querySelector('[data-testid="advisor-skipped"]') as HTMLElement;
    expect(skipped.textContent).toContain('TCS.NS');
    expect(skipped.textContent).toContain('no stored market move yet');
  });

  it('filters to a linked symbol when query params are present', async () => {
    tradeServiceMock.getAdviceSignals.mockReturnValue(
      of({
        accountId: 103,
        generatedAt: '2026-10-07T12:30:00Z',
        signals: [
          {
            symbol: 'ITC.NS',
            instrumentName: null,
            origin: 'HOLDING',
            signal: 'BUY',
            reasoning: ['Gain'],
            lastPrice: 505.45,
            changePct: null,
            unrealisedPnlPercent: 14,
            currency: 'INR',
            priceAsOf: '2026-10-07T12:25:00Z',
            stale: false
          },
          {
            symbol: 'AAPL',
            instrumentName: null,
            origin: 'HOLDING',
            signal: 'HOLD',
            reasoning: ['Flat'],
            lastPrice: 180,
            changePct: null,
            unrealisedPnlPercent: 0,
            currency: 'USD',
            priceAsOf: '2026-10-07T12:25:00Z',
            stale: false
          }
        ],
        skipped: []
      })
    );

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [AdvisorComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        { provide: TradeService, useValue: tradeServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ symbol: 'ITC.NS', origin: 'HOLDING' }) },
            queryParamMap: of(convertToParamMap({ symbol: 'ITC.NS', origin: 'HOLDING' }))
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="advisor-signal-card"]');
    expect(cards.length).toBe(1);
    expect(cards[0].textContent).toContain('ITC.NS');
    expect(fixture.nativeElement.querySelector('[data-testid="advisor-focus-banner"]')?.textContent).toContain('ITC.NS');
  });

  it('shows a friendly error message when loading fails', () => {
    tradeServiceMock.getAdviceSignals.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { errorCode: 'MKT-503', message: 'Pricing unavailable' }
          })
      )
    );
    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toContain('Live prices are unavailable right now');
  });
});

function buildToken(): string {
  const header = toBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = toBase64Url(
    JSON.stringify({ sub: 'team5', accountId: 103, roles: ['USER'], iat: 1_700_000_000, exp: 4_102_444_800 })
  );

  return `${header}.${payload}.sig`;
}

function toBase64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
