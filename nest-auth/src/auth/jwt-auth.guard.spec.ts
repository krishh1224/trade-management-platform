import { ExecutionContext, HttpException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt from 'jsonwebtoken';
import { AuthTokenService } from '../auth-token/auth-token.service';
import { AuthenticatedRequest } from './authenticated-request';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('JwtAuthGuard', () => {
  const jwtSecret = 'x'.repeat(32);
  const wrongJwtSecret = 'y'.repeat(32);
  const jwtIssuer = 'auth-service';
  const accessTokenTtlSeconds = 900;

  let guard: JwtAuthGuard;
  let tokenService: AuthTokenService;

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

    tokenService = new AuthTokenService(configService as unknown as ConfigService<any, true>);
    guard = new JwtAuthGuard(tokenService);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('accepts a valid token and attaches verified claims to request.user', () => {
    const token = tokenService.issueAccessToken({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    const request = buildRequest(`Bearer ${token}`);
    const result = guard.canActivate(buildExecutionContext(request));

    expect(result).toBe(true);
    expect(request.user).toEqual({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
      iat: Math.floor(Date.parse('2026-10-05T08:00:00.000Z') / 1000),
      exp: Math.floor(Date.parse('2026-10-05T08:00:00.000Z') / 1000) + accessTokenTtlSeconds,
      iss: jwtIssuer,
    });
  });

  it('rejects an expired token with AUTH-401', () => {
    const expiredToken = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 1,
        roles: ['CUSTOMER'],
      },
      jwtSecret,
      {
        algorithm: 'HS256',
        issuer: jwtIssuer,
        expiresIn: -60,
      },
    );

    const request = buildRequest(`Bearer ${expiredToken}`);

    expect(() => guard.canActivate(buildExecutionContext(request))).toThrow(HttpException);
    expectUnauthorisedEnvelope(
      getThrownHttpException(() => guard.canActivate(buildExecutionContext(request))),
    );
    expect(request.user).toBeUndefined();
  });

  it('rejects a wrong-signature token with AUTH-401 before claims are trusted', () => {
    const wrongSignatureToken = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 999,
        roles: ['ADMIN'],
      },
      wrongJwtSecret,
      {
        algorithm: 'HS256',
        issuer: jwtIssuer,
        expiresIn: accessTokenTtlSeconds,
      },
    );

    const request = buildRequest(`Bearer ${wrongSignatureToken}`);

    expect(() => guard.canActivate(buildExecutionContext(request))).toThrow(HttpException);
    expectUnauthorisedEnvelope(
      getThrownHttpException(() => guard.canActivate(buildExecutionContext(request))),
    );
    expect(request.user).toBeUndefined();
  });

  it('rejects malformed or missing Authorization headers with AUTH-401', () => {
    const malformedHeaders: Array<string | undefined> = [
      undefined,
      '',
      'Basic abc',
      'Bearer',
      'Bearer    ',
      'Bearer token extra',
    ];

    for (const malformedHeader of malformedHeaders) {
      const request = buildRequest(malformedHeader);
      expect(() => guard.canActivate(buildExecutionContext(request))).toThrow(HttpException);
      expectUnauthorisedEnvelope(
        getThrownHttpException(() => guard.canActivate(buildExecutionContext(request))),
      );
      expect(request.user).toBeUndefined();
    }
  });

  it('does not reach the controller handler when authentication fails', () => {
    const handler = jest.fn();
    const wrongSignatureToken = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 999,
        roles: ['ADMIN'],
      },
      wrongJwtSecret,
      {
        algorithm: 'HS256',
        issuer: jwtIssuer,
        expiresIn: accessTokenTtlSeconds,
      },
    );

    const request = buildRequest(`Bearer ${wrongSignatureToken}`);

    expect(() => {
      const allowed = guard.canActivate(buildExecutionContext(request));
      if (allowed) {
        handler(request.user);
      }
    }).toThrow(HttpException);

    expect(handler).not.toHaveBeenCalled();
    expect(request.user).toBeUndefined();
  });

  function buildRequest(authorizationHeader?: string): AuthenticatedRequest {
    const headers: Record<string, string> = {};
    if (authorizationHeader !== undefined) {
      headers.authorization = authorizationHeader;
    }

    return {
      headers,
    } as unknown as AuthenticatedRequest;
  }

  function buildExecutionContext(request: AuthenticatedRequest): ExecutionContext {
    return {
      switchToHttp: () => ({
        getRequest: () => request,
      }),
    } as unknown as ExecutionContext;
  }

  function getThrownHttpException(fn: () => unknown): HttpException {
    try {
      fn();
      throw new Error('Expected a HttpException to be thrown');
    } catch (error) {
      return error as HttpException;
    }
  }

  function expectUnauthorisedEnvelope(error: HttpException): void {
    expect(error.getStatus()).toBe(401);
    expect(error.getResponse()).toEqual({
      errorCode: 'AUTH-401',
      message: 'Unauthorised',
    });
  }
});