import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiConfiguration } from './generated/auth-api/api-configuration';
import { getCurrentUser } from './generated/auth-api/fn/profile/get-current-user';
import { login } from './generated/auth-api/fn/auth/login';
import { refresh } from './generated/auth-api/fn/auth/refresh';
import { register } from './generated/auth-api/fn/auth/register';
import {
  LoginRequest,
  RefreshRequest,
  RegisterRequest,
  TokenResponse,
  UserResponse
} from './generated/auth-api/models';

/** Hand-written facade over the generated Auth service client. Unwraps the HTTP envelope. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfiguration);

  register(body: RegisterRequest): Observable<UserResponse> {
    return register(this.http, this.config.rootUrl, { body }).pipe(map((r) => r.body));
  }

  login(body: LoginRequest): Observable<TokenResponse> {
    return login(this.http, this.config.rootUrl, { body }).pipe(map((r) => r.body));
  }

  refresh(body: RefreshRequest): Observable<TokenResponse> {
    return refresh(this.http, this.config.rootUrl, { body }).pipe(map((r) => r.body));
  }

  getCurrentUser(): Observable<UserResponse> {
    return getCurrentUser(this.http, this.config.rootUrl).pipe(map((r) => r.body));
  }

  updatePassword(currentPassword: string, newPassword: string): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${this.config.rootUrl}/auth/password`, {
      currentPassword,
      newPassword
    });
  }
}
