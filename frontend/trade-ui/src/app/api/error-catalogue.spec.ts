import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import {
  ERROR_MESSAGES_BY_CODE,
  NETWORK_UNREACHABLE_MESSAGE,
  PlatformErrorCode,
  toUserFacingErrorMessage
} from './error-catalogue';

const ALL_DOCUMENTED_CODES: PlatformErrorCode[] = [
  'ACC-404',
  'ACC-403',
  'INS-404',
  'ORD-400',
  'ORD-409',
  'CSH-400',
  'CSH-409',
  'VAL-422',
  'AUTH-401',
  'AUTH-409',
  'MKT-503',
  'WTL-404',
  'WTL-409',
  'STR-404'
];

describe('error catalogue', () => {
  it('maps every documented code to a non-empty user-facing message', () => {
    expect(Object.keys(ERROR_MESSAGES_BY_CODE).sort()).toEqual([...ALL_DOCUMENTED_CODES].sort());

    for (const code of ALL_DOCUMENTED_CODES) {
      const message = ERROR_MESSAGES_BY_CODE[code];
      expect(typeof message).toBe('string');
      expect(message.trim().length).toBeGreaterThan(0);
    }
  });

  it('returns the fallback sentence for an unrecognised code', () => {
    const fallback = 'Order history could not be loaded. Please try again.';
    const message = toUserFacingErrorMessage(
      new HttpErrorResponse({
        status: 500,
        error: { errorCode: 'UNK-999', message: 'Internal Server Error' }
      }),
      { fallbackMessage: fallback }
    );

    expect(message).toBe(fallback);
  });

  it('returns the network sentence for HTTP status 0 / no response', () => {
    const message = toUserFacingErrorMessage(
      new HttpErrorResponse({
        status: 0,
        error: { type: 'network' }
      }),
      { fallbackMessage: 'Request failed.' }
    );

    expect(message).toBe(NETWORK_UNREACHABLE_MESSAGE);
  });
});
