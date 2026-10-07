import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../auth/auth-session.service';
import { provideApi } from './provide-api';

describe('refresh token interceptor', () => {
  function setup() {
    let currentToken = 'expired-token';

    const authSession = {
      token: () => currentToken,
      refreshAccessToken: vi.fn(() => {
        currentToken = 'fresh-token';
        return of('fresh-token');
      }),
      signOut: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideApi({ tradeApi: 'http://trade', authApi: 'http://auth' }),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: authSession }
      ]
    });

    return {
      http: TestBed.inject(HttpClient),
      httpMock: TestBed.inject(HttpTestingController),
      authSession
    };
  }

  it('retries a protected request once after refreshing access token', () => {
    const { http, httpMock, authSession } = setup();
    let responseBody: unknown;

    http.get('http://trade/api/v1/accounts/101').subscribe((response) => {
      responseBody = response;
    });

    const initial = httpMock.expectOne('http://trade/api/v1/accounts/101');
    expect(initial.request.headers.get('Authorization')).toBe('Bearer expired-token');
    initial.flush({ errorCode: 'AUTH-401', message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(authSession.refreshAccessToken).toHaveBeenCalledTimes(1);

    const retried = httpMock.expectOne('http://trade/api/v1/accounts/101');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    retried.flush({ accountId: 101 });

    expect(responseBody).toEqual({ accountId: 101 });
    expect(authSession.signOut).not.toHaveBeenCalled();
    httpMock.verify();
  });

  it('signs out when refresh fails and returns the refresh error', () => {
    const { http, httpMock, authSession } = setup();

    authSession.refreshAccessToken.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized', url: 'http://auth/auth/refresh' }))
    );

    let capturedError: HttpErrorResponse | undefined;
    http.get('http://trade/api/v1/accounts/101').subscribe({
      error: (error) => {
        capturedError = error as HttpErrorResponse;
      }
    });

    const initial = httpMock.expectOne('http://trade/api/v1/accounts/101');
    initial.flush({ errorCode: 'AUTH-401', message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(authSession.refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(authSession.signOut).toHaveBeenCalledTimes(1);
    expect(capturedError?.status).toBe(401);
    expect(capturedError?.url).toBe('http://auth/auth/refresh');
    httpMock.verify();
  });

  it('does not attempt refresh for auth public endpoints', () => {
    const { http, httpMock, authSession } = setup();

    let capturedError: HttpErrorResponse | undefined;
    http.post('http://auth/auth/login', { username: 'u', password: 'p' }).subscribe({
      error: (error) => {
        capturedError = error as HttpErrorResponse;
      }
    });

    const loginRequest = httpMock.expectOne('http://auth/auth/login');
    loginRequest.flush({ errorCode: 'AUTH-401', message: 'bad credentials' }, { status: 401, statusText: 'Unauthorized' });

    expect(authSession.refreshAccessToken).not.toHaveBeenCalled();
    expect(authSession.signOut).not.toHaveBeenCalled();
    expect(capturedError?.status).toBe(401);
    httpMock.verify();
  });
});
