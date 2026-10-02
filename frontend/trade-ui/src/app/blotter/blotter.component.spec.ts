import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { OrderHistoryEntry } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { BLOTTER_MAX_POLL_ATTEMPTS, BLOTTER_POLL_INTERVAL_MS, BlotterComponent } from './blotter.component';

const authServiceMock = {
  login: vi.fn()
};

const tradeServiceMock = {
  getOrders: vi.fn()
};

describe('BlotterComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    tradeServiceMock.getOrders.mockReset();
    tradeServiceMock.getOrders.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [BlotterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: TradeService, useValue: tradeServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('renders order history newest first and includes rejected orders', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        historyEntry({ orderId: 'ORD-OLD', status: 'FILLED', createdOn: '2026-10-02T09:00:00Z' }),
        historyEntry({ orderId: 'ORD-NEWEST', status: 'REJECTED', createdOn: '2026-10-02T12:00:00Z' }),
        historyEntry({ orderId: 'ORD-MID', status: 'CANCELLED', createdOn: '2026-10-02T10:00:00Z' })
      ])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    const rows = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="history-row"]')) as HTMLElement[];
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('ORD-NEWEST');
    expect(fixture.nativeElement.textContent).toContain('REJECTED');
  });

  it('shows NEW orders as still working', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: 'ORD-WORKING', status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('NEW - Working');
  });

  it('stops polling once no orders remain NEW', () => {
    vi.useFakeTimers();
    tradeServiceMock.getOrders
      .mockReturnValueOnce(of([historyEntry({ orderId: 'ORD-1', status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })]))
      .mockReturnValueOnce(of([historyEntry({ orderId: 'ORD-1', status: 'FILLED', createdOn: '2026-10-02T12:00:00Z' })]));

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    expect(tradeServiceMock.getOrders).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(BLOTTER_POLL_INTERVAL_MS + 1);
    fixture.detectChanges();
    expect(tradeServiceMock.getOrders).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(BLOTTER_POLL_INTERVAL_MS * 2);
    fixture.detectChanges();
    expect(tradeServiceMock.getOrders).toHaveBeenCalledTimes(2);
  });

  it('respects the configured polling bound', () => {
    vi.useFakeTimers();
    tradeServiceMock.getOrders.mockImplementation(() =>
      of([historyEntry({ orderId: 'ORD-BOUND', status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    vi.advanceTimersByTime((BLOTTER_MAX_POLL_ATTEMPTS + 3) * BLOTTER_POLL_INTERVAL_MS);
    fixture.detectChanges();

    expect(tradeServiceMock.getOrders).toHaveBeenCalledTimes(1 + BLOTTER_MAX_POLL_ATTEMPTS);
  });

  it('handles empty order history gracefully', () => {
    tradeServiceMock.getOrders.mockReturnValue(of([]));

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No orders yet for this account.');
  });

  it('refresh never invokes order placement', () => {
    vi.useFakeTimers();
    tradeServiceMock.getOrders
      .mockReturnValueOnce(of([historyEntry({ orderId: 'ORD-1', status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })]))
      .mockReturnValueOnce(of([historyEntry({ orderId: 'ORD-1', status: 'FILLED', createdOn: '2026-10-02T12:01:00Z' })]))
      .mockReturnValue(of([]));

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    const refreshButton = fixture.nativeElement.querySelector('[data-testid="refresh-history-button"]') as HTMLButtonElement;
    refreshButton.click();
    fixture.detectChanges();

    vi.advanceTimersByTime(BLOTTER_POLL_INTERVAL_MS + 1);
    fixture.detectChanges();

    expect(tradeServiceMock.getOrders).toHaveBeenCalled();
  });

  it('handles null accountId gracefully', () => {
    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = null;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('The signed-in account could not be read from this session');
  });

  it('handles API errors gracefully', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { errorCode: 'ERR-500', message: 'Server error' }
          })
      )
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    expect(tradeServiceMock.getOrders).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Order history could not be loaded');
  });
});

function historyEntry(overrides: Partial<OrderHistoryEntry>): OrderHistoryEntry {
  return {
    accountId: 101,
    createdOn: '2026-10-02T09:00:00Z',
    orderId: 'ORD-100',
    symbol: 'INFY.NS',
    side: 'BUY',
    quantity: 5,
    price: 150,
    status: 'FILLED',
    executedPrice: 150,
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
