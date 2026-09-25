import { HttpException, HttpStatus } from '@nestjs/common';
import { AuthService } from './auth.service';
import { AuthTokenService } from '../auth-token/auth-token.service';
import { RefreshTokenService } from '../auth-token/refresh-token.service';
import { CredentialsRepository } from '../credentials/credentials.repository';
import { PasswordService } from '../credentials/password.service';

describe('AuthService', () => {
  let service: AuthService;
  let credentialsRepository: jest.Mocked<CredentialsRepository>;
  let passwordService: jest.Mocked<PasswordService>;
  let authTokenService: jest.Mocked<AuthTokenService>;
  let refreshTokenService: jest.Mocked<RefreshTokenService>;

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

    service = new AuthService(
      credentialsRepository,
      passwordService,
      authTokenService,
      refreshTokenService,
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

    const result = await service.login({
      username: 'priya.menon',
      password: 'correct horse battery staple',
    });

    expect(result).toEqual({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });
  });

  it('returns AUTH-401 for wrong credentials', async () => {
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
      service.login({ username: 'priya.menon', password: 'wrong' }),
    ).rejects.toMatchObject({
      status: HttpStatus.UNAUTHORIZED,
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });
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
