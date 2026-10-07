import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it } from 'vitest';

import { AuthService } from './auth.service';
import { RequestBuilder as AuthRequestBuilder } from './generated/auth-api/request-builder';
import { RequestBuilder as TradeRequestBuilder } from './generated/trade-api/request-builder';
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

  it('refreshes token against the auth API', () => {
    const http = setup();
    let token: string | undefined;

    TestBed.inject(AuthService)
      .refresh({ refreshToken: 'r' })
      .subscribe((t) => (token = t.accessToken));

    const req = http.expectOne((r) => r.url.startsWith('http://auth/') && r.method === 'POST');
    req.flush({ accessToken: 'new', expiresIn: 900, refreshToken: 'r2', tokenType: 'Bearer' });
    expect(token).toBe('new');
  });

  it('registers against the auth API', () => {
    const http = setup();
    let username: string | undefined;

    TestBed.inject(AuthService)
      .register({ accountId: 1, username: 'alice', password: 'Password!123' })
      .subscribe((u) => (username = u.username));

    const req = http.expectOne((r) => r.url.startsWith('http://auth/') && r.method === 'POST');
    req.flush({ id: 'user-1', accountId: 1, username: 'alice', roles: ['USER'] });
    expect(username).toBe('alice');
  });

  it('gets current user against the auth API', () => {
    const http = setup();
    let username: string | undefined;

    TestBed.inject(AuthService)
      .getCurrentUser()
      .subscribe((u) => (username = u.username));

    const req = http.expectOne((r) => r.url.startsWith('http://auth/') && r.method === 'GET');
    req.flush({ id: 'user-2', accountId: 2, username: 'bob', roles: ['USER'] });
    expect(username).toBe('bob');
  });

  it('updates password via explicit patch endpoint', () => {
    const http = setup();
    let message: string | undefined;

    TestBed.inject(AuthService)
      .updatePassword('Old!123456789', 'New!123456789')
      .subscribe((r) => (message = r.message));

    const req = http.expectOne((r) => r.url === 'http://auth/auth/password' && r.method === 'PATCH');
    expect(req.request.body).toEqual({
      currentPassword: 'Old!123456789',
      newPassword: 'New!123456789'
    });
    req.flush({ message: 'ok' });
    expect(message).toBe('ok');
  });

  it('loads instruments with prefix as query param', () => {
    const http = setup();
    let count = 0;

    TestBed.inject(TradeService)
      .getInstruments(5, 'AAP')
      .subscribe((rows) => (count = rows.length));

    const req = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/5/instruments' && r.method === 'GET');
    expect(req.request.params.get('prefix')).toBe('AAP');
    req.flush([{ symbol: 'AAPL', name: 'Apple', currency: 'USD' }]);
    expect(count).toBe(1);
  });

  it('loads watchlist groups and creates one', () => {
    const http = setup();
    const trade = TestBed.inject(TradeService);
    let loaded = 0;
    let createdName: string | undefined;

    trade.listWatchlistGroups().subscribe((g) => (loaded = g.length));
    http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists/groups' && r.method === 'GET').flush([
      { watchlistGroupId: 'g1', name: 'Main', createdAt: '2026-01-01T00:00:00Z' }
    ]);

    trade.createWatchlistGroup({ name: 'Tech' }).subscribe((g) => (createdName = g.name));
    const createReq = http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists/groups' && r.method === 'POST');
    expect(createReq.request.body).toEqual({ name: 'Tech' });
    createReq.flush({ watchlistGroupId: 'g2', name: 'Tech', createdAt: '2026-01-01T00:00:00Z' });

    expect(loaded).toBe(1);
    expect(createdName).toBe('Tech');
  });

  it('gets watchlist with and without a group filter', () => {
    const http = setup();
    const trade = TestBed.inject(TradeService);
    let allRows = 0;
    let groupedRows = 0;

    trade.getWatchlist(9).subscribe((rows) => (allRows = rows.length));
    const allReq = http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists' && r.method === 'GET');
    expect(allReq.request.params.has('groupId')).toBe(false);
    allReq.flush([]);

    trade.getWatchlist(9, 'g2').subscribe((rows) => (groupedRows = rows.length));
    const groupReq = http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists' && r.method === 'GET');
    expect(groupReq.request.params.get('groupId')).toBe('g2');
    groupReq.flush([
      {
        watchlistId: 'w1',
        watchlistGroupId: 'g2',
        watchlistName: 'Tech',
        instrumentId: 1,
        symbol: 'AAPL',
        instrumentName: 'Apple',
        addedAt: '2026-01-01T00:00:00Z',
        lastPrice: 180,
        changePct: 1.1
      }
    ]);

    expect(allRows).toBe(0);
    expect(groupedRows).toBe(1);
  });

  it('adds and removes watchlist rows', () => {
    const http = setup();
    const trade = TestBed.inject(TradeService);
    let symbol: string | undefined;
    let removed = false;

    trade
      .addToWatchlist({ accountId: 3, symbol: 'TSLA', watchlistGroupId: 'g3' })
      .subscribe((row) => (symbol = row.symbol));
    const addReq = http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists' && r.method === 'POST');
    expect(addReq.request.body).toEqual({ accountId: 3, symbol: 'TSLA', watchlistGroupId: 'g3' });
    addReq.flush({
      watchlistId: 'w9',
      watchlistGroupId: 'g3',
      watchlistName: 'Growth',
      instrumentId: 2,
      symbol: 'TSLA',
      instrumentName: 'Tesla',
      addedAt: '2026-01-01T00:00:00Z',
      lastPrice: 250,
      changePct: 0
    });

    trade.removeFromWatchlist('w9').subscribe(() => {
      removed = true;
    });
    http.expectOne((r) => r.url === 'http://trade/api/v1/watchlists/w9' && r.method === 'DELETE').flush({});

    expect(symbol).toBe('TSLA');
    expect(removed).toBe(true);
  });

  it('gets profile from account endpoint', () => {
    const http = setup();
    let profileName: string | undefined;

    TestBed.inject(TradeService)
      .getClientProfile(77)
      .subscribe((profile) => (profileName = profile.name));

    const req = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/77/profile' && r.method === 'GET');
    req.flush({ accountId: 77, name: 'Riya', email: 'riya@trade.test', phone: '12345', dob: '2000-01-01' });
    expect(profileName).toBe('Riya');
  });

  it('loads account, balance, positions and orders through generated wrappers', () => {
    const http = setup();
    const trade = TestBed.inject(TradeService);
    let holderName: string | undefined;
    let balance = 0;
    let positions = 0;
    let orders = 0;

    trade.getAccount(7).subscribe((a) => (holderName = a.holderName));
    http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7' && r.method === 'GET').flush({
      accountId: 7,
      holderName: 'Alice',
      status: 'ACTIVE',
      accountType: 'RETAIL',
      riskProfile: 'MODERATE'
    });

    trade.getBalance(7).subscribe((b) => (balance = b.cashBalance));
    http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/balance' && r.method === 'GET').flush({
      accountId: 7,
      cashBalance: 1250,
      heldForOrders: 20,
      availableToWithdraw: 1230,
      currency: 'INR',
      asOf: '2026-01-01T00:00:00Z'
    });

    trade.getPositions(7).subscribe((p) => (positions = p.length));
    http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/positions' && r.method === 'GET').flush([
      { symbol: 'AAPL', quantity: 1, averagePrice: 100, marketValue: 101, unrealizedPnl: 1 }
    ]);

    trade.getOrders(7, { status: 'FILLED', from: '2026-01-01T00:00:00Z' }).subscribe((o) => (orders = o.length));
    const ordersReq = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/orders' && r.method === 'GET');
    expect(ordersReq.request.params.get('status')).toBe('FILLED');
    expect(ordersReq.request.params.get('from')).toBe('2026-01-01T00:00:00Z');
    ordersReq.flush([
      {
        orderId: 'ORD-1',
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 1,
        price: 100,
        status: 'FILLED',
        createdOn: '2026-01-01T00:00:00Z'
      }
    ]);

    expect(holderName).toBe('Alice');
    expect(balance).toBe(1250);
    expect(positions).toBe(1);
    expect(orders).toBe(1);
  });

  it('cancels orders and handles deposit/withdraw/cash-history wrappers', () => {
    const http = setup();
    const trade = TestBed.inject(TradeService);
    let cancelStatus: string | undefined;
    let depositAmount = 0;
    let withdrawAmount = 0;
    let txCount = 0;

    trade.cancelOrder('123').subscribe((o) => (cancelStatus = o.status));
    http.expectOne((r) => r.url === 'http://trade/api/v1/orders/123' && r.method === 'DELETE').flush({
      orderId: '123',
      message: 'cancelled',
      price: 100,
      quantity: 1,
      side: 'BUY',
      status: 'CANCELLED',
      symbol: 'AAPL'
    });

    trade
      .deposit(7, { amount: 500, idempotencyKey: 'dep-1' })
      .subscribe((tx) => (depositAmount = tx.amount));
    const depReq = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/deposits' && r.method === 'POST');
    expect(depReq.request.body).toEqual({ amount: 500, idempotencyKey: 'dep-1' });
    depReq.flush({
      accountId: 7,
      amount: 500,
      balanceAfter: 1700,
      balanceBefore: 1200,
      createdAt: '2026-01-01T00:00:00Z',
      currency: 'INR',
      transactionId: 11,
      type: 'DEPOSIT'
    });

    trade
      .withdraw(7, { amount: 200, idempotencyKey: 'wd-1' })
      .subscribe((tx) => (withdrawAmount = tx.amount));
    const wdReq = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/withdrawals' && r.method === 'POST');
    expect(wdReq.request.body).toEqual({ amount: 200, idempotencyKey: 'wd-1' });
    wdReq.flush({
      accountId: 7,
      amount: 200,
      balanceAfter: 1500,
      balanceBefore: 1700,
      createdAt: '2026-01-01T00:00:00Z',
      currency: 'INR',
      transactionId: 12,
      type: 'WITHDRAW'
    });

    trade.getCashTransactions(7, 10).subscribe((rows) => (txCount = rows.length));
    const txReq = http.expectOne((r) => r.url === 'http://trade/api/v1/accounts/7/transactions' && r.method === 'GET');
    expect(txReq.request.params.get('limit')).toBe('10');
    txReq.flush([
      {
        accountId: 7,
        amount: 500,
        balanceAfter: 1700,
        balanceBefore: 1200,
        createdAt: '2026-01-01T00:00:00Z',
        currency: 'INR',
        transactionId: 11,
        type: 'DEPOSIT'
      }
    ]);

    expect(cancelStatus).toBe('CANCELLED');
    expect(depositAmount).toBe(500);
    expect(withdrawAmount).toBe(200);
    expect(txCount).toBe(1);
  });

  it('covers generated request builder variants for trade-api', () => {
    const rb = new TradeRequestBuilder('http://trade', '/items/{id}', 'post');
    rb.path('id', ['a', 'b'], { style: 'matrix', explode: true });
    rb.query('q', { a: 1, b: 'x' }, { explode: false });
    rb.query('deep', { x: 1 }, { style: 'deepObject' });
    rb.header('X-Trace', ['one', 'two']);
    rb.body({ amount: 10, idempotencyKey: 'k1' }, 'application/x-www-form-urlencoded');
    const req = rb.build({ accept: 'application/json', responseType: 'text', reportProgress: true });

    expect(req.url).toContain('/items/;id=a;id=b');
    expect(req.params.get('q')).toBe('a,1,b,x');
    expect(req.params.get('deep[x]')).toBe('1');
    expect(req.headers.getAll('X-Trace')).toEqual(['one', 'two']);
    expect(req.headers.get('Accept')).toBe('application/json');
    expect(req.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(req.body).toBe('amount=10&idempotencyKey=k1');

    const rbMulti = new TradeRequestBuilder('http://trade', '/upload', 'post');
    rbMulti.body({ file: ['a', 'b'], meta: { x: 1 } }, 'multipart/form-data');
    const uploadReq = rbMulti.build();
    expect(uploadReq.body instanceof FormData).toBe(true);
    expect(uploadReq.headers.has('Content-Type')).toBe(false);
  });

  it('covers generated request builder variants for auth-api', () => {
    const rb = new AuthRequestBuilder('http://auth', '/users/{id}', 'put');
    rb.path('id', 'u-1', { style: 'label' });
    rb.query('roles', ['USER', 'ADMIN'], { explode: true });
    rb.query('filter', { a: 1, b: 2 }, { explode: true });
    rb.header('X-Token', 'jwt');
    rb.body({ username: 'alice' }, 'application/json');

    const req = rb.build({ accept: 'application/json' });
    expect(req.url).toContain('/users/.u-1');
    expect(req.params.getAll('roles')).toEqual(['USER', 'ADMIN']);
    expect(req.params.get('a')).toBe('1');
    expect(req.params.get('b')).toBe('2');
    expect(req.headers.get('X-Token')).toBe('jwt');
    expect(req.headers.get('Content-Type')).toBe('application/json');
  });
});
