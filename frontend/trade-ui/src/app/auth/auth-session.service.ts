import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { AuthService } from '../api/auth.service';
import { LoginRequest, TokenResponse } from '../api/generated/auth-api/models';

const SESSION_STORAGE_KEY = 'trade-ui.auth.session';

/** Claims nest-auth puts in the access token. Decoded for display only, never trusted. */
export interface AccessTokenClaims {
  sub: string;
  accountId: number;
  roles: string[];
  iat: number;
  exp: number;
}

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly authService = inject(AuthService);
  private readonly session = signal<TokenResponse | null>(this.readStoredSession());

  readonly token = computed(() => this.session()?.accessToken ?? null);
  readonly isAuthenticated = computed(() => this.token() !== null);
  readonly claims = computed(() => decodeClaims(this.token()));

  /** True when a token is held and, where its expiry can be read, has not passed. */
  hasActiveSession(): boolean {
    if (!this.isAuthenticated()) {
      return false;
    }

    const exp = this.claims()?.exp;
    return exp === undefined || exp * 1000 > Date.now();
  }

  signIn(credentials: LoginRequest): Observable<void> {
    return this.authService.login(credentials).pipe(
      tap((response) => this.storeSession(response)),
      map(() => void 0)
    );
  }

  signOut(): void {
    this.session.set(null);
    localStorage.removeItem(SESSION_STORAGE_KEY);
  }

  private storeSession(session: TokenResponse): void {
    this.session.set(session);
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  }

  private readStoredSession(): TokenResponse | null {
    const rawSession = localStorage.getItem(SESSION_STORAGE_KEY);

    if (!rawSession) {
      return null;
    }

    try {
      return JSON.parse(rawSession) as TokenResponse;
    } catch {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }
}

function decodeClaims(token: string | null): AccessTokenClaims | null {
  const payload = token?.split('.')[1];

  if (!payload) {
    return null;
  }

  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')));

    if (typeof claims.accountId !== 'number' || typeof claims.exp !== 'number') {
      return null;
    }

    return {
      sub: String(claims.sub ?? ''),
      accountId: claims.accountId,
      roles: Array.isArray(claims.roles) ? claims.roles.map(String) : [],
      iat: typeof claims.iat === 'number' ? claims.iat : claims.exp,
      exp: claims.exp
    };
  } catch {
    return null;
  }
}
