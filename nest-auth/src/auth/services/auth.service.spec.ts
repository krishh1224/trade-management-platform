import { HttpException, HttpStatus } from '@nestjs/common';
import { AuthService } from './auth.service';
import { AuthTokenService } from '../../token/services/auth-token.service';
import { RefreshTokenService } from '../../token/services/refresh-token.service';
import { CredentialsRepository } from '../../credentials/credentials.repository';
import { PasswordService } from '../../credentials/password.service';
import { LoginThrottleService } from './login-throttle.service';

describe('AuthService', () => {
  let service: AuthService;
  let credentialsRepository: jest.Mocked<CredentialsRepository>;
  let passwordService: jest.Mocked<PasswordService>;
  let authTokenService: jest.Mocked<AuthTokenService>;
  let refreshTokenService: jest.Mocked<RefreshTokenService>;
  let loginThrottleService: jest.Mocked<LoginThrottleService>;

  beforeEach(() => {
    credentialsRepository = {
      findByAccountId: jest.fn(),
      findByUsername: jest.fn(),
      createCredential: jest.fn(),
    } as any;

    passwordService = {
      hash: jest.fn(),
      verify: jest.fn(),
    } as any;

    authTokenService = {
      issueAccessToken: jest.fn(),
      verifyAccessToken: jest.fn(),
    } as any;

    refreshTokenService = {
      validateRefreshToken: jest.fn(),
      issueRefreshToken: jest.fn(),
      storeRefreshToken: jest.fn(),
      consumeRefreshToken: jest.fn(),
      revokeFamily: jest.fn(),
      generateFamilyId: jest.fn(),
    } as any;

    loginThrottleService = {
      checkThrottle: jest.fn(),
      recordFailure: jest.fn(),
      clearFailures: jest.fn(),
    } as any;

    service = new AuthService(
      credentialsRepository,
      passwordService,
      authTokenService,
      refreshTokenService,
      loginThrottleService,
    );
  });

  it('registers successfully by inserting only into credentials and returns UserResponse', async () => {
    const createdOn = new Date('2026-10-05T08:00:00.000Z');
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn,
    });

    const result = await service.register({
      username: 'priya.menon',
      password: 'correct horse battery staple',
      accountId: 101,
    });

    expect(result.username).toBe('priya.menon');
    expect(result.accountId).toBe(101);
    expect(result.roles).toEqual(['USER']);
    expect(result.createdOn).toBe(createdOn.toISOString());

    expect(credentialsRepository.createCredential).toHaveBeenCalledWith(
      101,
      'priya.menon',
      '$2b$12$abc',
      'USER',
    );

    // Registration should not issue tokens
    expect(authTokenService.issueAccessToken).not.toHaveBeenCalled();
    expect(refreshTokenService.storeRefreshToken).not.toHaveBeenCalled();
  });

  it('returns AUTH-409 for duplicate registration', async () => {
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockRejectedValue({ code: '23505' });

    await expect(
      service.register({
        username: 'priya.menon',
        password: 'correct horse battery staple',
        accountId: 101,
      }),
    ).rejects.toMatchObject({
      status: HttpStatus.CONFLICT,
      response: { errorCode: 'AUTH-409', message: 'Registration failed' },
    });
  });

  it('logs in successfully and returns token pair', async () => {
    credentialsRepository.findByUsername.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'ADMIN',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });
    passwordService.verify.mockResolvedValue(true);
    authTokenService.issueAccessToken.mockReturnValue('access-token');
    refreshTokenService.generateFamilyId.mockReturnValue('family-id');
    refreshTokenService.issueRefreshToken.mockReturnValue({
      refreshToken: 'refresh-token',
      tokenHash: 'token-hash',
      familyId: 'family-id',
    });
    refreshTokenService.storeRefreshToken.mockResolvedValue(1);

    const result = await service.login(
      {
        username: 'priya.menon',
        password: 'correct horse battery staple',
      },
      '192.168.1.1',
    );

    expect(result).toEqual({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    // Verify throttle was cleared on successful login
    expect(loginThrottleService.clearFailures).toHaveBeenCalledWith('192.168.1.1');
  });

  it('returns AUTH-401 for wrong password', async () => {
    credentialsRepository.findByUsername.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });
    passwordService.verify.mockResolvedValue(false);

    await expect(
      service.login(
        { username: 'priya.menon', password: 'wrong' },
        '192.168.1.1',
      ),
    ).rejects.toMatchObject({
      status: HttpStatus.UNAUTHORIZED,
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });

    // Verify failure was recorded
    expect(loginThrottleService.recordFailure).toHaveBeenCalledWith('192.168.1.1');
  });

  it('returns AUTH-401 for unknown username', async () => {
    credentialsRepository.findByUsername.mockResolvedValue(null);
    passwordService.verify.mockResolvedValue(false); // Dummy hash will not match

    await expect(
      service.login(
        { username: 'unknown.user', password: 'any' },
        '192.168.1.1',
      ),
    ).rejects.toMatchObject({
      status: HttpStatus.UNAUTHORIZED,
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });

    // Verify failure was recorded
    expect(loginThrottleService.recordFailure).toHaveBeenCalledWith('192.168.1.1');
  });

  it('performs password verification for unknown username (timing-safe)', async () => {
    credentialsRepository.findByUsername.mockResolvedValue(null);
    passwordService.verify.mockResolvedValue(false);

    await expect(
      service.login(
        { username: 'unknown.user', password: 'test' },
        '192.168.1.1',
      ),
    ).rejects.toThrow();

    // Verify that passwordService.verify was called even though username not found
    // This ensures comparable timing for unknown username vs wrong password
    expect(passwordService.verify).toHaveBeenCalled();
    expect(passwordService.verify).toHaveBeenCalledWith('test', expect.any(String));
  });

  it('unknown username and wrong password return identical status and response body', async () => {
    const wrongPasswordCredential = {
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER' as const,
      passwordHash: '$2b$12$abc',
      createdOn: new Date(),
    };

    // Test 1: Wrong password
    credentialsRepository.findByUsername.mockResolvedValueOnce(wrongPasswordCredential);
    passwordService.verify.mockResolvedValueOnce(false);

    let error1: HttpException | undefined;
    try {
      await service.login({ username: 'priya.menon', password: 'wrong' }, '192.168.1.1');
    } catch (e) {
      error1 = e as HttpException;
    }

    // Test 2: Unknown username
    credentialsRepository.findByUsername.mockResolvedValueOnce(null);
    passwordService.verify.mockResolvedValueOnce(false);

    let error2: HttpException | undefined;
    try {
      await service.login({ username: 'unknown.user', password: 'any' }, '192.168.1.1');
    } catch (e) {
      error2 = e as HttpException;
    }

    // Both should return the same status
    expect(error1?.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
    expect(error2?.getStatus()).toBe(HttpStatus.UNAUTHORIZED);

    // Both should return the same response body
    expect(error1?.getResponse()).toEqual({
      errorCode: 'AUTH-401',
      message: 'Unauthorised',
    });
    expect(error2?.getResponse()).toEqual({
      errorCode: 'AUTH-401',
      message: 'Unauthorised',
    });
  });

  it('checks throttle before attempting login', async () => {
    loginThrottleService.checkThrottle.mockImplementation(() => {
      throw new HttpException(
        { errorCode: 'AUTH-401', message: 'Unauthorised' },
        HttpStatus.UNAUTHORIZED,
      );
    });

    await expect(
      service.login({ username: 'user', password: 'pass' }, '192.168.1.1'),
    ).rejects.toThrow();

    // Verify checkThrottle was called first
    expect(loginThrottleService.checkThrottle).toHaveBeenCalledWith('192.168.1.1');
  });

  it('records failure and throws throttle exception on max attempts', async () => {
    const callerIp = '192.168.1.1';
    credentialsRepository.findByUsername.mockResolvedValue(null);
    passwordService.verify.mockResolvedValue(false);

    // Simulate recordFailure throwing on max attempts
    loginThrottleService.recordFailure.mockImplementationOnce(() => {
      throw new HttpException(
        { errorCode: 'AUTH-401', message: 'Unauthorised' },
        HttpStatus.UNAUTHORIZED,
      );
    });

    await expect(
      service.login({ username: 'user', password: 'pass' }, callerIp),
    ).rejects.toThrow(HttpException);

    expect(loginThrottleService.recordFailure).toHaveBeenCalledWith(callerIp);
  });


  it('refresh succeeds for valid token', async () => {
    refreshTokenService.validateRefreshToken.mockResolvedValue({
      accountId: 101,
      userId: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      roles: ['ADMIN'],
      tokenId: 1,
      tokenHash: 'hash',
      familyId: 'family-1',
      isConsumed: false,
      isRevoked: false,
      isExpired: false,
    });
    authTokenService.issueAccessToken.mockReturnValue('new-access-token');
    refreshTokenService.generateFamilyId.mockReturnValue('family-2');
    refreshTokenService.issueRefreshToken.mockReturnValue({
      refreshToken: 'new-refresh-token',
      tokenHash: 'new-hash',
      familyId: 'family-2',
    });
    refreshTokenService.consumeRefreshToken.mockResolvedValue({} as any);
    refreshTokenService.storeRefreshToken.mockResolvedValue(2);

    const result = await service.refresh('refresh-token');

    expect(result).toEqual({
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
  });

  it('refresh returns AUTH-401 for invalid token', async () => {
    refreshTokenService.validateRefreshToken.mockRejectedValue(new Error('invalid'));

    await expect(service.refresh('bad-token')).rejects.toMatchObject({
      status: HttpStatus.UNAUTHORIZED,
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });
  });

  it('me returns authenticated user mapped from credentials', async () => {
    credentialsRepository.findByAccountId.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });

    const result = await service.me({
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 101,
      roles: ['USER'],
      iat: 1,
      exp: 2,
      iss: 'auth-service',
    });

    expect(result).toEqual({
      id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      username: 'priya.menon',
      accountId: 101,
      roles: ['USER'],
      createdOn: '2026-10-05T08:00:00.000Z',
    });
  });

  it('me returns AUTH-401 when no credential exists for account', async () => {
    credentialsRepository.findByAccountId.mockResolvedValue(null);

    await expect(
      service.me({
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 101,
        roles: ['USER'],
        iat: 1,
        exp: 2,
        iss: 'auth-service',
      }),
    ).rejects.toMatchObject({
      status: HttpStatus.UNAUTHORIZED,
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });
  });

  it('does not issue tokens during register', async () => {
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });

    await service.register({
      username: 'priya.menon',
      password: 'correct horse battery staple',
      accountId: 101,
    });

    expect(authTokenService.issueAccessToken).not.toHaveBeenCalled();
    expect(refreshTokenService.issueRefreshToken).not.toHaveBeenCalled();
  });

  it('uses credentials table interaction only for register path', async () => {
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockResolvedValue({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });

    await service.register({
      username: 'priya.menon',
      password: 'correct horse battery staple',
      accountId: 101,
    });

    expect(credentialsRepository.createCredential).toHaveBeenCalledTimes(1);
    expect(refreshTokenService.storeRefreshToken).not.toHaveBeenCalled();
  });

  it('propagates unexpected registration errors (for rollback-safe behavior)', async () => {
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockRejectedValue(new Error('db down'));

    await expect(
      service.register({
        username: 'priya.menon',
        password: 'correct horse battery staple',
        accountId: 101,
      }),
    ).rejects.toThrow('db down');
  });

  it('returns HttpException envelope for duplicate register', async () => {
    passwordService.hash.mockResolvedValue('$2b$12$abc');
    credentialsRepository.createCredential.mockRejectedValue({ code: '23505' });

    try {
      await service.register({
        username: 'priya.menon',
        password: 'correct horse battery staple',
        accountId: 101,
      });
      fail('Expected HttpException');
    } catch (error) {
      expect(error).toBeInstanceOf(HttpException);
      const httpError = error as HttpException;
      expect(httpError.getStatus()).toBe(HttpStatus.CONFLICT);
      expect(httpError.getResponse()).toEqual({
        errorCode: 'AUTH-409',
        message: 'Registration failed',
      });
    }
  });
});
