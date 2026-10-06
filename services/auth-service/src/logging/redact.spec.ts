import { redact } from './redact';

describe('redact', () => {
  it('redacts a top-level sensitive key', () => {
    expect(redact({ password: 'hunter2', email: 'a@b.com' })).toEqual({
      password: '[REDACTED]',
      email: 'a@b.com',
    });
  });

  it('redacts sensitive keys at any nesting depth', () => {
    const input = {
      user: { email: 'a@b.com', credentials: { passwordHash: '$2b$12$...' } },
    };

    expect(redact(input)).toEqual({
      user: { email: 'a@b.com', credentials: { passwordHash: '[REDACTED]' } },
    });
  });

  it('redacts sensitive keys inside arrays', () => {
    const input = { users: [{ email: 'a@b.com', password: 'x' }, { token: 'jwt' }] };

    expect(redact(input)).toEqual({
      users: [{ email: 'a@b.com', password: '[REDACTED]' }, { token: '[REDACTED]' }],
    });
  });

  it('matches key names case-insensitively and as snake_case', () => {
    const input = { PASSWORD: 'a', Password_Hash: 'b', API_KEY: 'c', Authorization: 'd' };

    expect(redact(input)).toEqual({
      PASSWORD: '[REDACTED]',
      Password_Hash: '[REDACTED]',
      API_KEY: '[REDACTED]',
      Authorization: '[REDACTED]',
    });
  });

  it('leaves non-sensitive keys and primitives untouched', () => {
    expect(redact({ email: 'a@b.com', age: 30, active: true, notes: null })).toEqual({
      email: 'a@b.com',
      age: 30,
      active: true,
      notes: null,
    });
  });

  it('handles circular references without looping forever', () => {
    const input: Record<string, unknown> = { email: 'a@b.com' };
    input.self = input;

    expect(redact(input)).toEqual({ email: 'a@b.com', self: '[CIRCULAR]' });
  });

  it('passes through primitives and arrays of primitives unchanged', () => {
    expect(redact('plain string')).toBe('plain string');
    expect(redact(42)).toBe(42);
    expect(redact([1, 2, 3])).toEqual([1, 2, 3]);
  });
});
