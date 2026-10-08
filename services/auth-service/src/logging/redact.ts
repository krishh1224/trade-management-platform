const REDACTED = '[REDACTED]';

// Key names checked case-insensitively, and matched anywhere as a substring
// so both "password" and "passwordHash"/"password_hash" are caught without
// having to enumerate every naming convention.
const SENSITIVE_KEY_PATTERNS = [
  'password',
  'secret',
  'token',
  'authorization',
  'apikey',
  'api_key',
];

function isSensitiveKey(key: string): boolean {
  const normalised = key.toLowerCase();
  return SENSITIVE_KEY_PATTERNS.some((pattern) => normalised.includes(pattern));
}

/**
 * Deep-clones `value`, replacing any value whose key matches a sensitive
 * pattern with "[REDACTED]", at any nesting depth (objects inside arrays
 * inside objects, and so on). Non-plain values (Date, primitives, etc.) pass
 * through unchanged unless their own key is sensitive.
 */
export function redact(value: unknown, seen = new WeakSet<object>()): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => redact(item, seen));
  }

  if (value !== null && typeof value === 'object') {
    if (seen.has(value)) {
      return '[CIRCULAR]';
    }
    seen.add(value);

    if (value instanceof Date || value instanceof Error) {
      return value;
    }

    const result: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
      result[key] = isSensitiveKey(key) ? REDACTED : redact(val, seen);
    }
    return result;
  }

  return value;
}
