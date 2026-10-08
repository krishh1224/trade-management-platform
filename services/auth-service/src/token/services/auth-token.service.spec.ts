import { ConfigService } from '@nestjs/config';
import jwt, { JwtHeader, JwtPayload } from 'jsonwebtoken';
import { AuthTokenService } from './auth-token.service';

describe('AuthTokenService', () => {
  const jwtSecret = 'x'.repeat(32);
  const jwtIssuer = 'auth-service';
  const accessTokenTtlSeconds = 900;

  let service: AuthTokenService;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-10-05T08:00:00.000Z'));

    const configService = {
      get: (key: string) =>
        (
          {
            JWT_SECRET: jwtSecret,
            JWT_ISSUER: jwtIssuer,
            ACCESS_TOKEN_TTL_SECONDS: accessTokenTtlSeconds,
          } as Record<string, string | number>
        )[key],
    } as ConfigService;

    service = new AuthTokenService(configService as unknown as ConfigService<any, true>);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('issues an HS256 access token with exactly the contract claim set and values', () => {
    const token = service.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    const verified = jwt.verify(token, jwtSecret, {
      algorithms: ['HS256'],
      issuer: jwtIssuer,
    }) as JwtPayload;

    const nowEpochSeconds = Math.floor(Date.parse('2026-10-05T08:00:00.000Z') / 1000);

    expect(typeof verified.sub).toBe('string');
    expect(verified.sub).toBe('8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f');

    expect(typeof verified.accountId).toBe('number');
    expect(verified.accountId).toBe(1);

    expect(Array.isArray(verified.roles)).toBe(true);
    expect(verified.roles).toEqual(['CUSTOMER']);

    expect(typeof verified.iat).toBe('number');
    expect(verified.iat).toBe(nowEpochSeconds);

    expect(typeof verified.exp).toBe('number');
    expect(verified.exp).toBe(nowEpochSeconds + accessTokenTtlSeconds);

    expect(typeof verified.iss).toBe('string');
    expect(verified.iss).toBe(jwtIssuer);
  });

  it('verifies the token signature with the configured service key', () => {
    const token = service.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 42,
      roles: ['CUSTOMER'],
    });

    const verified = jwt.verify(token, jwtSecret, {
      algorithms: ['HS256'],
      issuer: jwtIssuer,
    }) as JwtPayload;

    // Contract "account" semantics map to the accountId claim: numeric trading account key.
    expect(verified.accountId).toBe(42);
    expect(typeof verified.accountId).toBe('number');
  });

  it('contains no claims outside the contract-defined set', () => {
    const token = service.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    const verified = jwt.verify(token, jwtSecret, {
      algorithms: ['HS256'],
      issuer: jwtIssuer,
    }) as JwtPayload;

    const actualKeys = Object.keys(verified).sort();
    const expectedKeys = ['accountId', 'exp', 'iat', 'iss', 'roles', 'sub'].sort();
    expect(actualKeys).toEqual(expectedKeys);

    expect(verified).not.toHaveProperty('email');
    expect(verified).not.toHaveProperty('username');
    expect(verified).not.toHaveProperty('role');
    expect(verified).not.toHaveProperty('account');
  });

  it('sets HS256 in the token header', () => {
    const token = service.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    const decoded = jwt.decode(token, { complete: true }) as {
      header: JwtHeader;
      payload: JwtPayload;
    };

    expect(decoded.header.alg).toBe('HS256');
  });

  it('fails verification when the secret is different', () => {
    const token = service.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    expect(() =>
      jwt.verify(token, 'y'.repeat(32), {
        algorithms: ['HS256'],
        issuer: jwtIssuer,
      }),
    ).toThrow();
  });
});