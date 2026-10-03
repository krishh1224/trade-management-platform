import { DEFAULT_RETURN_URL, isSafeReturnUrl, safeReturnUrl } from './return-url';

describe('return address', () => {
  it.each(['https://evil.com', 'http://evil.com/x', '//evil.com', '/\\evil.com', '\\\\evil.com', 'javascript:alert(1)', '/\t/evil.com', '/a\nb', 'evil.com', '', null, undefined, 42, '/sign-in', '/sign-in?returnUrl=/'])(
    'an off-origin or unsafe return address is refused: %j',
    (value) => {
      expect(isSafeReturnUrl(value)).toBe(false);
      expect(safeReturnUrl(value)).toBe(DEFAULT_RETURN_URL);
    }
  );

  it.each(['/', '/orders', '/orders?status=NEW#top'])('a path on this origin is kept: %s', (value) => {
    expect(safeReturnUrl(value)).toBe(value);
  });
});
