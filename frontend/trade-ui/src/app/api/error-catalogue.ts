import { HttpErrorResponse } from '@angular/common/http';

import type { ErrorResponse as AuthErrorResponse } from './generated/auth-api/models/error-response';
import type { ErrorResponse as TradeErrorResponse } from './generated/trade-api/models/error-response';

// MKT-503 belongs to contracts/portfolio-api.yaml and WTL-404 / WTL-409 to contracts/watchlists-api.yaml,
// which the generated Trade API client does not cover.
export type PlatformErrorCode =
  | TradeErrorResponse['errorCode']
  | AuthErrorResponse['errorCode']
  | 'MKT-503'
  | 'WTL-404'
  | 'WTL-409';

export const ERROR_MESSAGES_BY_CODE: Readonly<Record<PlatformErrorCode, string>> = {
  'ACC-404': 'The trading account could not be found.',
  'ACC-403': 'This account is not active for trading, or this session cannot access it.',
  'INS-404': 'The selected instrument is not available for trading.',
  'ORD-400': 'There is not enough cash available for this order.',
  'ORD-409': 'This order conflicts with your holdings, duplicates an earlier request, or can no longer be changed.',
  'CSH-400': 'That amount is more than the cash available to withdraw. Funds held for open buy orders cannot be withdrawn.',
  'CSH-409': 'This cash movement was already submitted or the balance just changed. Check your balance and try again.',
  'VAL-422': 'One or more fields are invalid. Review the form values and try again.',
  'AUTH-401': 'Authentication failed. Sign in again and retry.',
  'AUTH-409': 'That username is already in use. Choose a different username.',
  'MKT-503': 'Live prices are unavailable right now. Your holdings are still shown at cost.',
  'WTL-404': 'That watchlist item or price alert no longer exists. Refresh the page and try again.',
  'WTL-409': 'That already exists or cannot be changed: check the watchlist name, the instrument or the price alert.'
};

export const NETWORK_UNREACHABLE_MESSAGE =
  'The service could not be reached. Check your connection, then verify the API service and CORS settings.';

export function toUserFacingErrorMessage(error: unknown, options: { fallbackMessage: string }): string {
  if (isStatusZero(error)) {
    return NETWORK_UNREACHABLE_MESSAGE;
  }

  const errorCode = readErrorCode(error);
  if (errorCode !== null && isPlatformErrorCode(errorCode)) {
    return ERROR_MESSAGES_BY_CODE[errorCode];
  }

  return options.fallbackMessage;
}

function isPlatformErrorCode(value: string): value is PlatformErrorCode {
  return value in ERROR_MESSAGES_BY_CODE;
}

function isStatusZero(error: unknown): boolean {
  if (error instanceof HttpErrorResponse) {
    return error.status === 0;
  }

  if (typeof error !== 'object' || error === null || !('status' in error)) {
    return false;
  }

  return (error as { status?: unknown }).status === 0;
}

/** The platform `errorCode` of a failed call, or null. Branch on this, never on the message. */
export function readErrorCode(error: unknown): string | null {
  const body = readErrorBody(error);
  if (typeof body !== 'object' || body === null || !('errorCode' in body)) {
    return null;
  }

  const errorCode = (body as { errorCode?: unknown }).errorCode;
  return typeof errorCode === 'string' ? errorCode : null;
}

function readErrorBody(error: unknown): unknown {
  if (error instanceof HttpErrorResponse) {
    return error.error;
  }

  if (typeof error !== 'object' || error === null || !('error' in error)) {
    return null;
  }

  return (error as { error?: unknown }).error;
}
