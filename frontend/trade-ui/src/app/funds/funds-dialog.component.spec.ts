import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { BalanceResponse, CashTransactionResponse } from '../api/generated/trade-api/models';
import { TradeService } from '../api/trade.service';
import { FundsDialogComponent } from './funds-dialog.component';

const SESSION_KEY = 'trade-ui.auth.session';

function storeSessionFor(accountId: number): void {
  const exp = Math.floor(Date.now() / 1000) + 600;
  const payload = btoa(JSON.stringify({ sub: 'team5', accountId, roles: ['USER'], iat: exp - 900, exp }));
  localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({ accessToken: `h.${payload}.s`, expiresIn: 900, refreshToken: 'r', tokenType: 'Bearer' })
  );
}

function balance(cash: number, held = 0): BalanceResponse {
  return {
    accountId: 101,
    cashBalance: cash,
    heldForOrders: held,
    availableToWithdraw: Math.max(cash - held, 0),
    currency: 'INR',
    asOf: '2026-10-02T10:00:00Z'
  };
}

function ledger(type: 'DEPOSIT' | 'WITHDRAWAL', amount: number, before: number, after: number): CashTransactionResponse {
  return {
    transactionId: 4821937465,
    accountId: 101,
    type,
    amount,
    currency: 'INR',
    balanceBefore: before,
    balanceAfter: after,
    createdAt: '2026-10-02T10:14:22Z'
  };
}

function apiError(status: number, errorCode?: string): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: errorCode ? { errorCode, message: 'from the API' } : null });
}

describe('FundsDialogComponent', () => {
  let trade: {
    getBalance: ReturnType<typeof vi.fn>;
    getCashTransactions: ReturnType<typeof vi.fn>;
    deposit: ReturnType<typeof vi.fn>;
    withdraw: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    localStorage.clear();
    storeSessionFor(101);

    trade = {
      getBalance: vi.fn().mockReturnValue(of(balance(200000))),
      getCashTransactions: vi.fn().mockReturnValue(of([])),
      deposit: vi.fn(),
      withdraw: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [FundsDialogComponent],
      providers: [
        { provide: TradeService, useValue: trade },
        { provide: AuthService, useValue: { login: vi.fn() } }
      ]
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  function render(): ComponentFixture<FundsDialogComponent> {
    const fixture = TestBed.createComponent(FundsDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<FundsDialogComponent>, testId: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`);
  }

  function typeAmount(fixture: ComponentFixture<FundsDialogComponent>, value: string): void {
    const input = el(fixture, 'funds-amount') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function click(fixture: ComponentFixture<FundsDialogComponent>, testId: string): void {
    (el(fixture, testId) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('loads the wallet balance and shows an empty history gracefully', () => {
    const fixture = render();

    expect(trade.getBalance).toHaveBeenCalledWith(101);
    expect(trade.getCashTransactions).toHaveBeenCalledWith(101, 10);
    expect(el(fixture, 'funds-balance')?.textContent).toContain('2,00,000.00');
    expect(el(fixture, 'funds-activity-empty')?.textContent).toContain('No deposits or withdrawals yet.');
  });

  it('a valid deposit is sent once with an idempotency key and shows the new balance', () => {
    trade.deposit.mockReturnValue(of(ledger('DEPOSIT', 50000, 200000, 250000)));
    const fixture = render();
    trade.getBalance.mockReturnValue(of(balance(250000)));
    trade.getCashTransactions.mockReturnValue(of([ledger('DEPOSIT', 50000, 200000, 250000)]));

    typeAmount(fixture, '50000');
    click(fixture, 'funds-submit');

    expect(trade.deposit).toHaveBeenCalledTimes(1);
    const [accountId, body] = trade.deposit.mock.calls[0];
    expect(accountId).toBe(101);
    expect(body.amount).toBe(50000);
    expect(body.idempotencyKey.length).toBeGreaterThanOrEqual(8);
    expect(el(fixture, 'funds-result')?.textContent).toContain('deposited successfully');
    expect(el(fixture, 'funds-result')?.textContent).not.toContain('→');
    expect(el(fixture, 'funds-balance')?.textContent).toContain('2,50,000.00');
    expect(el(fixture, 'funds-activity')?.textContent).toContain('DEPOSIT');
  });

  it.each(['0', '-5', '10.005', 'abc'])('an invalid amount %s is blocked before submission', (value) => {
    const fixture = render();

    typeAmount(fixture, value);

    expect((el(fixture, 'funds-submit') as HTMLButtonElement).disabled).toBe(true);
    fixture.componentInstance.submit();
    expect(trade.deposit).not.toHaveBeenCalled();
  });

  it('a withdrawal above what is available is blocked before submission', () => {
    trade.getBalance.mockReturnValue(of(balance(50000, 12000)));
    const fixture = render();

    click(fixture, 'funds-tab-withdraw');
    typeAmount(fixture, '38000.01');

    expect((el(fixture, 'funds-submit') as HTMLButtonElement).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('You can withdraw at most');
    expect(fixture.nativeElement.textContent).toContain('held for BUY orders');
    fixture.componentInstance.submit();
    expect(trade.withdraw).not.toHaveBeenCalled();
  });

  it('a withdrawal asks for confirmation, then sends', () => {
    trade.withdraw.mockReturnValue(of(ledger('WITHDRAWAL', 20000, 200000, 180000)));
    const fixture = render();

    click(fixture, 'funds-tab-withdraw');
    typeAmount(fixture, '20000');
    click(fixture, 'funds-submit');

    expect(trade.withdraw).not.toHaveBeenCalled();
    expect(el(fixture, 'funds-confirm')).not.toBeNull();

    click(fixture, 'funds-confirm');

    expect(trade.withdraw).toHaveBeenCalledTimes(1);
    expect(el(fixture, 'funds-result')?.textContent).toContain('withdrawn successfully');
  });

  it('a CSH-400 from the API shows the mapped message, not the API text', () => {
    trade.withdraw.mockReturnValue(throwError(() => apiError(400, 'CSH-400')));
    const fixture = render();

    click(fixture, 'funds-tab-withdraw');
    typeAmount(fixture, '1000');
    click(fixture, 'funds-submit');
    click(fixture, 'funds-confirm');

    const message = el(fixture, 'funds-error')?.textContent ?? '';
    expect(message).toContain("That's more than you can withdraw right now.");
    expect(message).not.toContain('from the API');
  });

  it('a request that never reached the service renders a readable sentence', () => {
    trade.deposit.mockReturnValue(throwError(() => apiError(0)));
    const fixture = render();

    typeAmount(fixture, '100');
    click(fixture, 'funds-submit');

    expect(el(fixture, 'funds-error')?.textContent).toContain("We can't reach the trading service right now.");
  });

  it('retrying the same transfer reuses its idempotency key; a new amount gets a new one', () => {
    trade.deposit.mockReturnValue(throwError(() => apiError(0)));
    const fixture = render();

    typeAmount(fixture, '100');
    click(fixture, 'funds-submit');
    click(fixture, 'funds-submit');
    typeAmount(fixture, '200');
    click(fixture, 'funds-submit');

    const keys = trade.deposit.mock.calls.map(([, body]) => body.idempotencyKey);
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it('closing the dialog notifies the parent', () => {
    const fixture = render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    click(fixture, 'funds-close');

    expect(closed).toHaveBeenCalledTimes(1);
  });
});
