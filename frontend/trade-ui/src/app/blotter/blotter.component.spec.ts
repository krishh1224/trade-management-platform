import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { OrderHistoryRow, TradeService } from '../api/trade.service';
import { BLOTTER_MAX_POLL_ATTEMPTS, BLOTTER_POLL_INTERVAL_MS, BlotterComponent } from './blotter.component';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';

const authServiceMock = {
  login: vi.fn()
};

const tradeServiceMock = {
  getOrders: vi.fn(),
  getBalance: vi.fn(),
  cancelOrder: vi.fn(),
  emailOrderHistoryExport: vi.fn()
};

const WORKING_UUID = '3e701b47-1375-478c-bfef-aa638eff477d';

describe('BlotterComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    tradeServiceMock.getOrders.mockReset();
    tradeServiceMock.getOrders.mockReturnValue(of([]));
    tradeServiceMock.getBalance.mockReset();
    tradeServiceMock.getBalance.mockReturnValue(of({ cashBalance: 1000, currency: 'INR' }));
    tradeServiceMock.cancelOrder.mockReset();
    tradeServiceMock.emailOrderHistoryExport.mockReset();
    tradeServiceMock.emailOrderHistoryExport
      .mockReturnValue(of({ message: 'Order history PDF will be sent to your registered email.' }));

    await TestBed.configureTestingModule({
      imports: [BlotterComponent],
      providers: [
        provideNotificationCountStub(),
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
    expect(fixture.componentInstance.orderHistory()[0]?.orderId).toBe('ORD-NEWEST');
    expect(fixture.nativeElement.textContent).toContain('REJECTED');
  });

  it('uses the settled INR amount so cash after stays exact across USD fills', () => {
    // Real figures: 5 lakh, then BUY 1 NVDA (233.98 USD) = 22,502.08 INR and BUY 1 AAPL = 32,095.14 INR.
    tradeServiceMock.getBalance.mockReturnValue(of({ cashBalance: 445402.78, currency: 'INR' }));
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        historyEntry({ orderId: 'ORD-AAPL', symbol: 'AAPL', quantity: 1, executedPrice: 333.73, settlementAmount: 32095.14, createdOn: '2026-10-02T12:00:00Z' }),
        historyEntry({ orderId: 'ORD-NVDA', symbol: 'NVDA', quantity: 1, executedPrice: 233.98, settlementAmount: 22502.08, createdOn: '2026-10-02T11:00:00Z' }),
        historyEntry({ orderId: 'ORD-REJ', symbol: 'NVDA', status: 'REJECTED', executedPrice: undefined, createdOn: '2026-10-02T10:00:00Z' })
      ])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    const component = fixture.componentInstance;
    component.accountId = 101;
    fixture.detectChanges();

    expect(component.cashAfterFor('ORD-AAPL')).toBeCloseTo(445402.78, 2);
    expect(component.cashAfterFor('ORD-NVDA')).toBeCloseTo(477497.92, 2); // + 32,095.14
    expect(component.cashAfterFor('ORD-REJ')).toBeCloseTo(500000, 2); // + 22,502.08: the 5 lakh before either buy
  });

  it('rebuilds cash after from INR fills but does not guess past a foreign-currency fill', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        historyEntry({ orderId: 'ORD-INR-NEW', symbol: 'INFY.NS', quantity: 5, executedPrice: 150, createdOn: '2026-10-02T12:00:00Z' }),
        historyEntry({ orderId: 'ORD-USD', symbol: 'AAPL', quantity: 1, price: 340, executedPrice: 333.73, createdOn: '2026-10-02T11:00:00Z' }),
        historyEntry({ orderId: 'ORD-INR-OLD', symbol: 'INFY.NS', quantity: 5, executedPrice: 150, createdOn: '2026-10-02T10:00:00Z' })
      ])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    const component = fixture.componentInstance;
    component.accountId = 101;
    fixture.detectChanges();

    // Current balance is 1000. The newest INR buy (5 x 150 = 750) is undone for the row below it.
    expect(component.cashAfterFor('ORD-INR-NEW')).toBe(1000);
    // The USD fill's own cash-after is still exact (it comes from the newer rows)...
    expect(component.cashAfterFor('ORD-USD')).toBe(1750);
    // ...but undoing it would subtract 333.73 dollars from rupees, so older rows have no value.
    expect(component.cashAfterFor('ORD-INR-OLD')).toBeNull();
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

  it('filters by any combination of statuses and clears back to all', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        historyEntry({ orderId: 'ORD-F', status: 'FILLED', createdOn: '2026-10-02T12:00:00Z' }),
        historyEntry({ orderId: 'ORD-R', status: 'REJECTED', createdOn: '2026-10-02T11:00:00Z' }),
        historyEntry({ orderId: 'ORD-N', status: 'NEW', createdOn: '2026-10-02T10:00:00Z' }),
        historyEntry({ orderId: 'ORD-C', status: 'CANCELLED', createdOn: '2026-10-02T09:00:00Z' })
      ])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    const component = fixture.componentInstance;
    component.accountId = 101;
    fixture.detectChanges();
    const shown = () => component.filteredOrderHistory().map((row) => row.orderId);

    expect(shown()).toEqual(['ORD-F', 'ORD-R', 'ORD-N', 'ORD-C']);

    click(fixture, 'status-chip-REJECTED');
    expect(shown()).toEqual(['ORD-R']);

    click(fixture, 'status-chip-CANCELLED');
    expect(shown()).toEqual(['ORD-R', 'ORD-C']);
    expect(fixture.nativeElement.querySelector('[data-testid="status-chip-CANCELLED"]')?.getAttribute('aria-pressed')).toBe('true');

    click(fixture, 'status-chip-REJECTED');
    expect(shown()).toEqual(['ORD-C']);

    component.clearFilters();
    fixture.detectChanges();
    expect(shown()).toHaveLength(4);
    expect(fixture.nativeElement.querySelector('[data-testid="status-chip-CANCELLED"]')?.getAttribute('aria-pressed')).toBe('false');
  });

  it('offers Cancel only on working orders that have a UUID order id', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([
        historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'NEW', createdOn: '2026-10-02T12:00:00Z' }),
        historyEntry({ orderId: 'ORD-123', status: 'NEW', createdOn: '2026-10-02T11:00:00Z' }),
        historyEntry({ orderId: 'ORD-2e701b47-1375-478c-bfef-aa638eff477d', status: 'FILLED', createdOn: '2026-10-02T10:00:00Z' }),
        historyEntry({ orderId: 'ORD-1e701b47-1375-478c-bfef-aa638eff477d', status: 'REJECTED', createdOn: '2026-10-02T09:00:00Z' }),
        historyEntry({ orderId: 'ORD-0e701b47-1375-478c-bfef-aa638eff477d', status: 'CANCELLED', createdOn: '2026-10-02T08:00:00Z' })
      ])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="order-cancel"]')).toHaveLength(1);
  });

  it('cancels a working order after confirmation and marks it cancelled', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })])
    );
    tradeServiceMock.cancelOrder.mockReturnValue(of({ orderId: `ORD-${WORKING_UUID}`, status: 'CANCELLED' }));

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'CANCELLED', createdOn: '2026-10-02T12:00:00Z' })])
    );
    click(fixture, 'order-cancel');
    click(fixture, 'order-cancel-yes');

    expect(tradeServiceMock.cancelOrder).toHaveBeenCalledTimes(1);
    expect(tradeServiceMock.cancelOrder).toHaveBeenCalledWith(WORKING_UUID);
    expect(fixture.componentInstance.orderHistory()[0]?.status).toBe('CANCELLED');
    expect(fixture.nativeElement.querySelector('[data-testid="order-cancel"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="cancel-feedback"]')?.textContent).toContain('cancelled');
  });

  it('does nothing when the cancel confirmation is dismissed', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })])
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    click(fixture, 'order-cancel');
    click(fixture, 'order-cancel-no');

    expect(tradeServiceMock.cancelOrder).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="order-cancel"]')).not.toBeNull();
  });

  it('explains a cancel that lost the race to the executor and refreshes the history', () => {
    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'NEW', createdOn: '2026-10-02T12:00:00Z' })])
    );
    tradeServiceMock.cancelOrder.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { errorCode: 'ORD-409', message: 'Invalid order state transition' }
          })
      )
    );

    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    tradeServiceMock.getOrders.mockReturnValue(
      of([historyEntry({ orderId: `ORD-${WORKING_UUID}`, status: 'FILLED', createdOn: '2026-10-02T12:00:00Z' })])
    );
    click(fixture, 'order-cancel');
    click(fixture, 'order-cancel-yes');

    const feedback = fixture.nativeElement.querySelector('[data-testid="cancel-feedback"]') as HTMLElement;
    expect(feedback.getAttribute('role')).toBe('alert');
    expect(feedback.textContent).toContain('can no longer be changed');
    expect(tradeServiceMock.getOrders).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.orderHistory()[0]?.status).toBe('FILLED');
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

  it('queues order-history PDF email and shows success feedback', () => {
    const fixture = TestBed.createComponent(BlotterComponent);
    fixture.componentInstance.accountId = 101;
    fixture.detectChanges();

    click(fixture, 'email-history-button');

    expect(tradeServiceMock.emailOrderHistoryExport).toHaveBeenCalledWith(101);
    expect(fixture.nativeElement.querySelector('[data-testid="export-feedback"]')?.textContent)
      .toContain('Order history PDF will be sent');
  });

  it('shows export error feedback when email queueing fails', () => {
    tradeServiceMock.emailOrderHistoryExport.mockReturnValueOnce(
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

    click(fixture, 'email-history-button');

    const feedback = fixture.nativeElement.querySelector('[data-testid="export-feedback"]') as HTMLElement;
    expect(feedback.getAttribute('role')).toBe('alert');
    expect(feedback.textContent).toContain('Could not queue the order history PDF email');
  });
});

function historyEntry(overrides: Partial<OrderHistoryRow>): OrderHistoryRow {
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

function click(fixture: { nativeElement: HTMLElement; detectChanges(): void }, testId: string): void {
  const button = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement | null;
  if (button === null) {
    throw new Error(`No element with data-testid="${testId}"`);
  }
  button.click();
  fixture.detectChanges();
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
