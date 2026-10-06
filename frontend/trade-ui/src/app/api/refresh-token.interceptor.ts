import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthSessionService } from '../auth/auth-session.service';
import { ApiUrls } from './provide-api';

const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);
const AUTH_PUBLIC_PATHS = new Set(['/auth/login', '/auth/register', '/auth/refresh']);

/**
 * Factory for an interceptor that refreshes expired access tokens once on HTTP 401,
 * then retries the original request transparently.
 */
export function refreshTokenInterceptor(urls: ApiUrls): HttpInterceptorFn {
  const authOrigin = parseOrigin(urls.authApi);

  return (request, next) => {
    const authSession = inject(AuthSessionService);

    return next(request).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
          return throwError(() => error);
        }

        if (request.context.get(RETRIED_AFTER_REFRESH)) {
          authSession.signOut();
          return throwError(() => error);
        }

        const requestUrl = parseAbsoluteUrl(request.url);
        if (requestUrl && authOrigin && requestUrl.origin === authOrigin && AUTH_PUBLIC_PATHS.has(requestUrl.pathname)) {
          return throwError(() => error);
        }

        return authSession.refreshAccessToken().pipe(
          switchMap((newAccessToken) => {
            if (!newAccessToken) {
              authSession.signOut();
              return throwError(() => error);
            }

            const retriedRequest = request.clone({
              setHeaders: { Authorization: `Bearer ${newAccessToken}` },
              context: request.context.set(RETRIED_AFTER_REFRESH, true)
            });

            return next(retriedRequest);
          }),
          catchError((refreshError) => {
            authSession.signOut();
            return throwError(() => refreshError);
          })
        );
      })
    );
  };
}

function parseOrigin(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).origin;
  } catch {
    return null;
  }
}

function parseAbsoluteUrl(rawUrl: string): URL | null {
  try {
    return new URL(rawUrl);
  } catch {
    return null;
  }
}
