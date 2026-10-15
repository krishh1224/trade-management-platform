import { newIdempotencyKey } from './idempotency-key';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newIdempotencyKey', () => {
  it('returns a UUID v4', () => {
    expect(newIdempotencyKey()).toMatch(UUID_V4);
  });

  it('still returns a UUID v4 on an http page where crypto.randomUUID is missing', () => {
    const original = crypto.randomUUID;
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      const a = newIdempotencyKey();
      const b = newIdempotencyKey();
      expect(a).toMatch(UUID_V4);
      expect(a).not.toBe(b);
    } finally {
      Object.defineProperty(crypto, 'randomUUID', { value: original, configurable: true });
    }
  });
});
