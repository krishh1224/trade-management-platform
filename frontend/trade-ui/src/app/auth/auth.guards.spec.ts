import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { AuthService } from '../api/auth.service';
import { authGuard, guestGuard } from './auth.guards';

const SESSION_KEY = 'trade-ui.auth.session';

function tokenExpiringAt(exp: number): string {
  const payload = btoa(JSON.stringify({ sub: 'u', accountId: 1, roles: ['CUSTOMER'], iat: exp - 900, exp }));
  return `h.${payload}.s`;
}

function storeSession(accessToken: string): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken, expiresIn: 900, refreshToken: 'r', tokenType: 'Bearer' }));
}

function runAuthGuard(url: string) {
  const state = { url } as RouterStateSnapshot;
  return TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state));
}

function runGuestGuard(returnUrl?: string) {
  const snapshot = {
    queryParamMap: { get: (name: string) => (name === 'returnUrl' ? (returnUrl ?? null) : null) }
  } as unknown as ActivatedRouteSnapshot;
  return TestBed.runInInjectionContext(() => guestGuard(snapshot, {} as RouterStateSnapshot));
}

async function resolveGuardResult(result: ReturnType<typeof runAuthGuard> | ReturnType<typeof runGuestGuard>) {
  return isObservable(result) ? await firstValueFrom(result) : result;
}

describe('auth guards', () => {
  const authServiceMock = {
    login: vi.fn(),
    refresh: vi.fn(() => of({
      accessToken: tokenExpiringAt(Math.floor(Date.now() / 1000) + 900),
      expiresIn: 900,
      refreshToken: 'r2',
      tokenType: 'Bearer' as const
    }))
  };

  beforeEach(() => {
    localStorage.clear();
    authServiceMock.login.mockReset();
    authServiceMock.refresh.mockClear();
    authServiceMock.refresh.mockImplementation(() => of({
      accessToken: tokenExpiringAt(Math.floor(Date.now() / 1000) + 900),
      expiresIn: 900,
      refreshToken: 'r2',
      tokenType: 'Bearer' as const
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }] });
  });

  afterEach(() => localStorage.clear());

  it('the guard blocks unauthenticated navigation and redirects to sign-in with the return address', async () => {
    const result = (await resolveGuardResult(runAuthGuard('/orders?status=NEW'))) as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/sign-in?returnUrl=%2Forders%3Fstatus%3DNEW');
  });

  it('the guard allows authenticated navigation', async () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));

    expect(await resolveGuardResult(runAuthGuard('/orders'))).toBe(true);
    expect(authServiceMock.refresh).not.toHaveBeenCalled();
  });

  it('an expired token is refreshed and treated as signed in', async () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) - 10));

    expect(await resolveGuardResult(runAuthGuard('/orders'))).toBe(true);
    const guestResult = (await resolveGuardResult(runGuestGuard())) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(guestResult)).toBe('/');
    expect(authServiceMock.refresh).toHaveBeenCalled();
  });

  it('refresh failure redirects to sign-in', async () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) - 10));
    authServiceMock.refresh.mockImplementation(() =>
      throwError(() => new Error('refresh failed'))
    );

    const result = (await resolveGuardResult(runAuthGuard('/orders'))) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/sign-in?returnUrl=%2Forders');
  });

  it('a signed-in visitor on sign-in is sent to a safe return address', async () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));
    const router = TestBed.inject(Router);

    expect(router.serializeUrl((await resolveGuardResult(runGuestGuard('/orders'))) as UrlTree)).toBe('/orders');
  });

  it('an off-origin return address is refused by the guest guard', async () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));
    const router = TestBed.inject(Router);

    expect(router.serializeUrl((await resolveGuardResult(runGuestGuard('https://evil.com'))) as UrlTree)).toBe('/');
    expect(router.serializeUrl((await resolveGuardResult(runGuestGuard('//evil.com'))) as UrlTree)).toBe('/');
  });
});
