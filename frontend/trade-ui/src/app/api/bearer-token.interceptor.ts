import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthSessionService } from '../auth/auth-session.service';
import { ApiUrls } from './provide-api';

const TRADE_PROTECTED_PATH_PATTERNS: readonly RegExp[] = [
  /^\/api\/v1\/orders$/,
  /^\/api\/v1\/orders\/[^/]+$/,
  /^\/api\/v1\/accounts\/[^/]+$/,
  /^\/api\/v1\/accounts\/[^/]+\/profile$/,
  /^\/api\/v1\/accounts\/[^/]+\/balance$/,
  /^\/api\/v1\/accounts\/[^/]+\/positions$/,
  /^\/api\/v1\/accounts\/[^/]+\/orders$/,
  /^\/api\/v1\/accounts\/[^/]+\/orders\/export-email$/,
  /^\/api\/v1\/accounts\/[^/]+\/advisor\/signals$/,
  /^\/api\/v1\/accounts\/[^/]+\/preferences$/,
  /^\/api\/v1\/accounts\/[^/]+\/notifications$/,
  /^\/api\/v1\/accounts\/[^/]+\/notifications\/[^/]+$/,
  /^\/api\/v1\/accounts\/[^/]+\/notifications\/[^/]+\/read$/,
  /^\/api\/v1\/accounts\/[^/]+\/price-alerts$/,
  /^\/api\/v1\/accounts\/[^/]+\/price-alerts\/[^/]+$/,
  /^\/api\/v1\/accounts\/[^/]+\/strategies$/,
  /^\/api\/v1\/accounts\/[^/]+\/strategies\/[^/]+$/,
  /^\/api\/v1\/accounts\/[^/]+\/strategies\/[^/]+\/(enable|disable|executions|chart)$/,
  /^\/api\/v1\/accounts\/[^/]+\/push-subscription$/,
  /^\/api\/v1\/accounts\/[^/]+\/push-subscription\/config$/,
  /^\/api\/v1\/accounts\/[^/]+\/(deposits|withdrawals|transactions)$/,
  /^\/api\/v1\/accounts\/[^/]+\/instruments$/,
  /^\/api\/v1\/portfolio\/[^/]+$/,
  /^\/api\/v1\/portfolio\/[^/]+\/positions$/,
  /^\/api\/v1\/portfolio\/[^/]+\/pnl$/,
  /^\/api\/v1\/portfolio\/[^/]+\/candles\/[^/]+$/,
  /^\/api\/v1\/watchlists$/,
  /^\/api\/v1\/watchlists\/[^/]+$/,
  /^\/api\/v1\/watchlists\/groups$/,
  /^\/api\/v1\/watchlists\/groups\/[^/]+$/
];

const ADVISOR_PROTECTED_PATH_PATTERNS: readonly RegExp[] = [/^\/api\/v1\/advisor\/chat$/];

const AUTH_ME_PATH = '/auth/me';
const AUTH_PASSWORD_PATH = '/auth/password';
const AUTH_PUBLIC_PATHS = new Set(['/auth/login', '/auth/register', '/auth/refresh']);

/**
 * Factory for a functional interceptor that conditionally attaches bearer tokens
 * only to allow-listed platform origins and protected API paths.
 */
export function bearerTokenInterceptor(urls: ApiUrls): HttpInterceptorFn {
  const tradeOrigin = parseOrigin(urls.tradeApi);
  const authOrigin = parseOrigin(urls.authApi);
  const advisorOrigin = urls.advisorApi ? parseOrigin(urls.advisorApi) : null;

  return (request, next) => {
    const authSession = inject(AuthSessionService);
    const token = authSession.token();

    if (!token) {
      return next(request);
    }

    const requestUrl = parseAbsoluteUrl(request.url);
    if (!requestUrl) {
      return next(request);
    }

    if (
      shouldAttachToken(requestUrl, tradeOrigin, authOrigin) ||
      (advisorOrigin !== null &&
        requestUrl.origin === advisorOrigin &&
        ADVISOR_PROTECTED_PATH_PATTERNS.some((pattern) => pattern.test(requestUrl.pathname)))
    ) {
      return next(
        request.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        })
      );
    }

    return next(request);
  };
}

function shouldAttachToken(requestUrl: URL, tradeOrigin: string | null, authOrigin: string | null): boolean {
  // Both APIs can share one origin (single-hostname ingress), so check each by path, not origin alone.
  if (
    tradeOrigin &&
    requestUrl.origin === tradeOrigin &&
    TRADE_PROTECTED_PATH_PATTERNS.some((pattern) => pattern.test(requestUrl.pathname))
  ) {
    return true;
  }

  if (authOrigin && requestUrl.origin === authOrigin) {
    if (requestUrl.pathname === AUTH_ME_PATH || requestUrl.pathname === AUTH_PASSWORD_PATH) {
      return true;
    }

    if (AUTH_PUBLIC_PATHS.has(requestUrl.pathname)) {
      return false;
    }

    return false;
  }

  return false;
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
