export const DEFAULT_RETURN_URL = '/';

/**
 * A return address is accepted only if it is a path on this origin. Anything else (another host,
 * a scheme, a protocol-relative `//host`, backslash tricks, control characters) is refused, because
 * an unchecked return address on a sign-in page is an open redirect.
 */
export function isSafeReturnUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return false;
  }

  // Browsers read `\` as `/` and strip tabs and newlines, so `/\evil.com` and `/\t/evil.com` escape the origin.
  if (value.includes('\\') || [...value].some((char) => char.charCodeAt(0) < 0x20 || char.charCodeAt(0) === 0x7f)) {
    return false;
  }

  const origin = window.location.origin;

  try {
    const parsed = new URL(value, origin);

    // Sending a signed-in user back to sign-in would loop.
    return parsed.origin === origin && parsed.pathname !== '/sign-in';
  } catch {
    return false;
  }
}

/** Returns the address if it is safe, otherwise the default landing page. */
export function safeReturnUrl(value: unknown): string {
  return isSafeReturnUrl(value) ? value : DEFAULT_RETURN_URL;
}
