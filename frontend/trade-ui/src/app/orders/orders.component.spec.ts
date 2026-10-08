import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { TradeService } from '../api/trade.service';
import { OrdersComponent } from './orders.component';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';

const authServiceMock = {
  login: vi.fn()
};

const tradeServiceMock = {
  placeOrder: vi.fn(),
  getAccount: vi.fn(),
  getBalance: vi.fn(),
  getInstruments: vi.fn()
};

describe('OrdersComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );

    tradeServiceMock.placeOrder.mockReset();
    tradeServiceMock.getAccount.mockReset();
    tradeServiceMock.getInstruments.mockReset();
    tradeServiceMock.getBalance.mockReset();
    tradeServiceMock.getAccount.mockReturnValue(of({ accountId: 101, status: 'ACTIVE' }));
    tradeServiceMock.getBalance.mockReturnValue(
      of({ accountId: 101, cashBalance: 98000, currency: 'INR', asOf: '2026-10-04T00:00:00Z' })
    );
    tradeServiceMock.getInstruments.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: TradeService, useValue: tradeServiceMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('a valid order submits and shows the returned status', () => {
    tradeServiceMock.placeOrder.mockReturnValue(
      of({
        orderId: 'ORD-200',
        status: 'NEW',
        message: 'Order accepted for execution.',
        symbol: 'INFY.NS',
        side: 'BUY',
        quantity: 5,
        price: 150
      })
    );

    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.setValue({ symbol: 'INFY.NS', side: 'BUY', quantity: '5', price: '150.00' });
    component.submitOrder();
    fixture.detectChanges();

    expect(tradeServiceMock.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: 101,
        symbol: 'INFY.NS',
        side: 'BUY',
        quantity: 5,
        price: 150,
        idempotencyKey: expect.any(String)
      })
    );
    expect(component.orderStatus()).toBe('NEW');
    expect(component.orderSuccessMessage()).toContain('returned status NEW');
    expect(fixture.nativeElement.textContent).toContain('NEW');
  });

  it('an invalid quantity is blocked before submission', () => {
    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: '0', price: '10.50' });
    component.submitOrder();

    expect(tradeServiceMock.placeOrder).not.toHaveBeenCalled();
    expect(component.orderForm.controls.quantity.invalid).toBe(true);
  });

  it('a business-rule rejection shows the mapped message', () => {
    tradeServiceMock.placeOrder.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { errorCode: 'ORD-400', message: 'Insufficient funds' }
          })
      )
    );

    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: '2', price: '200.00' });
    component.submitOrder();

    expect(component.orderErrorMessage()).toContain('not enough cash');
  });

  it('a USD instrument is priced in USD and notes INR settlement at the live rate', () => {
    tradeServiceMock.getInstruments.mockReturnValue(of([{ symbol: 'AAPL', name: 'Apple Inc.', currency: 'USD' }]));

    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.controls.symbol.setValue('AAPL');
    fixture.detectChanges();

    expect(component.currentSymbolCurrency()).toBe('USD');
    expect(component.settlementNote()).toContain('settled from your INR balance at the live USD/INR rate');
    expect(fixture.nativeElement.querySelector('[data-testid="order-settlement-note"]')).not.toBeNull();
    // the label shows the currency symbol: $ for USD, ₹ for INR, the code for anything else
    expect(fixture.nativeElement.textContent).toContain('Price ($)');
  });

  it('an INR instrument on an INR account shows no settlement note', () => {
    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.controls.symbol.setValue('INFY.NS');
    fixture.detectChanges();

    expect(component.currentSymbolCurrency()).toBe('INR');
    expect(component.settlementNote()).toBe('');
    expect(fixture.nativeElement.querySelector('[data-testid="order-settlement-note"]')).toBeNull();
  });

  it('filters instrument suggestions by both ticker and instrument name', () => {
    tradeServiceMock.getInstruments.mockReturnValue(
      of([
        { symbol: 'AAPL', name: 'Apple Inc.', currency: 'USD' },
        { symbol: 'MSFT', name: 'Microsoft Corporation', currency: 'USD' }
      ])
    );

    const fixture = TestBed.createComponent(OrdersComponent);
    const component = fixture.componentInstance;

    component.orderForm.controls.symbol.setValue('msf');
    component.onInstrumentInput();
    expect(component.filteredInstrumentOptions().map((option) => option.symbol)).toEqual(['MSFT']);

    component.orderForm.controls.symbol.setValue('apple');
    component.onInstrumentInput();
    expect(component.filteredInstrumentOptions().map((option) => option.symbol)).toEqual(['AAPL']);
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
