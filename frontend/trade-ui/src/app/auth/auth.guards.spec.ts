import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';

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

describe('auth guards', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { login: vi.fn() } }] });
  });

  afterEach(() => localStorage.clear());

  it('the guard blocks unauthenticated navigation and redirects to sign-in with the return address', () => {
    const result = runAuthGuard('/orders?status=NEW') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/sign-in?returnUrl=%2Forders%3Fstatus%3DNEW');
  });

  it('the guard allows authenticated navigation', () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));

    expect(runAuthGuard('/orders')).toBe(true);
  });

  it('an expired token is treated as signed out', () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) - 10));

    expect(runAuthGuard('/orders')).toBeInstanceOf(UrlTree);
    expect(runGuestGuard()).toBe(true);
  });

  it('a signed-in visitor on sign-in is sent to a safe return address', () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(runGuestGuard('/orders') as UrlTree)).toBe('/orders');
  });

  it('an off-origin return address is refused by the guest guard', () => {
    storeSession(tokenExpiringAt(Math.floor(Date.now() / 1000) + 600));
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(runGuestGuard('https://evil.com') as UrlTree)).toBe('/');
    expect(router.serializeUrl(runGuestGuard('//evil.com') as UrlTree)).toBe('/');
  });
});
