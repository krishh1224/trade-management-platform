import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthSessionService } from '../auth/auth-session.service';
import { provideApi } from './provide-api';

describe('bearer token interceptor', () => {
  function setup(token: string | null) {
    TestBed.configureTestingModule({
      providers: [
        provideApi({ tradeApi: 'http://trade', authApi: 'http://auth' }),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: { token: () => token } }
      ]
    });

    return {
      http: TestBed.inject(HttpClient),
      httpMock: TestBed.inject(HttpTestingController)
    };
  }

  it('a request to a platform API carries the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('http://trade/api/v1/accounts/101').subscribe();

    const req = httpMock.expectOne('http://trade/api/v1/accounts/101');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush({});
  });

  it('portfolio endpoints carry the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('http://trade/api/v1/portfolio/101').subscribe();
    http.get('http://trade/api/v1/portfolio/101/positions').subscribe();
    http.get('http://trade/api/v1/portfolio/101/candles/INFY.NS?range=3M').subscribe();

    for (const path of [
      '/api/v1/portfolio/101',
      '/api/v1/portfolio/101/positions',
      '/api/v1/portfolio/101/candles/INFY.NS?range=3M'
    ]) {
      const req = httpMock.expectOne(`http://trade${path}`);
      expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
      req.flush({});
    }
  });

  it('watchlist group endpoints carry the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('http://trade/api/v1/watchlists/groups').subscribe();

    const req = httpMock.expectOne('http://trade/api/v1/watchlists/groups');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush([]);
  });

  it('the advisor chat endpoint carries the bearer token, other advisor paths do not', () => {
    TestBed.configureTestingModule({
      providers: [
        provideApi({ tradeApi: 'http://trade', authApi: 'http://auth', advisorApi: 'http://advisor' }),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: { token: () => 'jwt-token' } }
      ]
    });
    const http = TestBed.inject(HttpClient);
    const httpMock = TestBed.inject(HttpTestingController);

    http.post('http://advisor/api/v1/advisor/chat', { messages: [] }).subscribe();
    http.get('http://advisor/health').subscribe();

    const chatReq = httpMock.expectOne('http://advisor/api/v1/advisor/chat');
    expect(chatReq.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    chatReq.flush({});

    const healthReq = httpMock.expectOne('http://advisor/health');
    expect(healthReq.request.headers.has('Authorization')).toBe(false);
    healthReq.flush({});
  });

  it('a request to a third-party origin does not carry the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('https://example.com/analytics/pixel').subscribe();

    const req = httpMock.expectOne('https://example.com/analytics/pixel');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('the unauthenticated auth routes are called without a header', () => {
    const { http, httpMock } = setup('jwt-token');

    http.post('http://auth/auth/login', { username: 'u', password: 'p' }).subscribe();
    http.post('http://auth/auth/register', { username: 'u', password: 'p', accountId: 101 }).subscribe();
    http.post('http://auth/auth/refresh', { refreshToken: 'r' }).subscribe();

    const loginReq = httpMock.expectOne('http://auth/auth/login');
    expect(loginReq.request.headers.has('Authorization')).toBe(false);
    loginReq.flush({});

    const registerReq = httpMock.expectOne('http://auth/auth/register');
    expect(registerReq.request.headers.has('Authorization')).toBe(false);
    registerReq.flush({});

    const refreshReq = httpMock.expectOne('http://auth/auth/refresh');
    expect(refreshReq.request.headers.has('Authorization')).toBe(false);
    refreshReq.flush({});
  });

  it('the funds routes carry the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.post('http://trade/api/v1/accounts/101/deposits', {}).subscribe();
    http.post('http://trade/api/v1/accounts/101/withdrawals', {}).subscribe();
    http.get('http://trade/api/v1/accounts/101/transactions?limit=10').subscribe();

    for (const url of [
      'http://trade/api/v1/accounts/101/deposits',
      'http://trade/api/v1/accounts/101/withdrawals',
      'http://trade/api/v1/accounts/101/transactions?limit=10'
    ]) {
      const req = httpMock.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
      req.flush({});
    }
  });

  it('the protected auth route carries the bearer token', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('http://auth/auth/me').subscribe();

    const req = httpMock.expectOne('http://auth/auth/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush({});
  });

  it('does not add authorization when no token exists', () => {
    const { http, httpMock } = setup(null);

    http.get('http://trade/api/v1/accounts/101').subscribe();

    const req = httpMock.expectOne('http://trade/api/v1/accounts/101');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('passes through requests with non-parseable URLs', () => {
    const { http, httpMock } = setup('jwt-token');

    http.get('/relative/path').subscribe();

    const req = httpMock.expectOne('/relative/path');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
