import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';

import { AuthService } from './auth.service';
import { provideApi } from './provide-api';
import { TradeService } from './trade.service';

describe('generated client facades', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideApi({ tradeApi: 'http://trade', authApi: 'http://auth' }), provideHttpClientTesting()]
    });
    return TestBed.inject(HttpTestingController);
  }

  it('places an order against the trade API and unwraps the body', () => {
    const http = setup();
    let status: string | undefined;
    TestBed.inject(TradeService)
      .placeOrder({ accountId: 1, idempotencyKey: 'k', price: 10, quantity: 2, side: 'BUY', symbol: 'AAPL' })
      .subscribe((o) => (status = o.status));

    const req = http.expectOne((r) => r.url.startsWith('http://trade/') && r.method === 'POST');
    req.flush({ orderId: 'x', message: 'ok', price: 10, quantity: 2, side: 'BUY', status: 'NEW', symbol: 'AAPL' });
    expect(status).toBe('NEW');
  });

  it('logs in against the auth API', () => {
    const http = setup();
    let token: string | undefined;
    TestBed.inject(AuthService)
      .login({ username: 'u', password: 'p' })
      .subscribe((t) => (token = t.accessToken));

    const req = http.expectOne((r) => r.url.startsWith('http://auth/'));
    req.flush({ accessToken: 'jwt', expiresIn: 900, refreshToken: 'r', tokenType: 'Bearer' });
    expect(token).toBe('jwt');
  });
});
