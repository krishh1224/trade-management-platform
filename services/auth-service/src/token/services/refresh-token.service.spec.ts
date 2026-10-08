import { ConfigService } from '@nestjs/config';
import { RefreshTokenService } from './refresh-token.service';
import { RefreshTokensRepository } from '../../credentials/refresh-tokens.repository';
import { RedactingLogger } from '../../logging/redacting-logger.service';

describe('RefreshTokenService', () => {
  let service: RefreshTokenService;
  let repository: jest.Mocked<RefreshTokensRepository>;
  let configService: jest.Mocked<ConfigService>;
  let logger: jest.Mocked<RedactingLogger>;

  beforeEach(() => {
    repository = {
      create: jest.fn(),
      findByHash: jest.fn(),
      consume: jest.fn(),
      revokeFamily: jest.fn(),
      findLiveTokensForAccount: jest.fn(),
    } as any;

    configService = {
      get: jest.fn((key: string) => {
        const values: Record<string, any> = {
          REFRESH_TOKEN_TTL_DAYS: 7,
        };
        return values[key];
      }),
    } as any;

    logger = {
      error: jest.fn(),
      warn: jest.fn(),
      log: jest.fn(),
    } as any;

    service = new RefreshTokenService(repository, configService, logger);
  });

  describe('issueRefreshToken', () => {
    it('generates an opaque random refresh token', () => {
      const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';
      const result = service.issueRefreshToken(familyId);

      // Should be 256 bits (32 bytes) hex-encoded = 64 hex characters
      expect(result.refreshToken).toMatch(/^[a-f0-9]{64}$/);
      expect(result.tokenHash).toMatch(/^[a-f0-9]{64}$/); // SHA256 hash
      expect(result.familyId).toBe(familyId);
    });

    it('generates different tokens on each call', () => {
      const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';
      const result1 = service.issueRefreshToken(familyId);
      const result2 = service.issueRefreshToken(familyId);

      expect(result1.refreshToken).not.toBe(result2.refreshToken);
      expect(result1.tokenHash).not.toBe(result2.tokenHash);
    });

    it('returns a hash (SHA256) of the token, not the token itself', () => {
      const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';
      const result = service.issueRefreshToken(familyId);

      // Hash should be 64 hex chars (SHA256)
      expect(result.tokenHash).toMatch(/^[a-f0-9]{64}$/);
      // Hash should NOT be the token itself
      expect(result.tokenHash).not.toBe(result.refreshToken);
    });
  });

  describe('validateRefreshToken', () => {
    const presentedToken = 'a' + 'b'.repeat(63); // Valid format
    const userId = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
    const accountId = 1;
    const roles = ['CUSTOMER'];
    const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';

    it('looks up token by hash and returns validation result', async () => {
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      repository.findByHash.mockResolvedValue({
        id: 42,
        accountId,
        userId,
        roles,
        tokenHash: 'abc123',
        familyId,
        expiresAt,
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.validateRefreshToken(presentedToken);

      expect(result).toEqual({
        accountId,
        userId,
        roles,
        tokenId: 42,
        tokenHash: 'abc123',
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      expect(repository.findByHash).toHaveBeenCalled();
      const hashArg = (repository.findByHash as jest.Mock).mock.calls[0][0];
      expect(hashArg).toMatch(/^[a-f0-9]{64}$/); // Should be hashed
      expect(hashArg).not.toBe(presentedToken); // Should NOT be the raw token
    });

    it('detects expired tokens', async () => {
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() - 1); // Expired yesterday

      repository.findByHash.mockResolvedValue({
        id: 42,
        accountId,
        userId,
        roles,
        tokenHash: 'abc123',
        familyId,
        expiresAt,
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.validateRefreshToken(presentedToken);

      expect(result.isExpired).toBe(true);
    });

    it('detects consumed tokens', async () => {
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      repository.findByHash.mockResolvedValue({
        id: 42,
        accountId,
        userId,
        roles,
        tokenHash: 'abc123',
        familyId,
        expiresAt,
        consumedAt: new Date(), // Already consumed
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.validateRefreshToken(presentedToken);

      expect(result.isConsumed).toBe(true);
    });

    it('detects revoked tokens', async () => {
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      repository.findByHash.mockResolvedValue({
        id: 42,
        accountId,
        userId,
        roles,
        tokenHash: 'abc123',
        familyId,
        expiresAt,
        consumedAt: null,
        revokedAt: new Date(), // Already revoked
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.validateRefreshToken(presentedToken);

      expect(result.isRevoked).toBe(true);
    });

    it('throws error when token not found', async () => {
      repository.findByHash.mockResolvedValue(null);

      try {
        await service.validateRefreshToken(presentedToken);
        fail('Expected error');
      } catch (error) {
        if (error instanceof Error) {
          expect(error.message).toBe('Invalid refresh token');
        } else {
          throw error;
        }
      }
    });

    it('copies roles array to prevent mutation', async () => {
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const storedRoles = ['CUSTOMER'];

      repository.findByHash.mockResolvedValue({
        id: 42,
        accountId,
        userId,
        roles: storedRoles,
        tokenHash: 'abc123',
        familyId,
        expiresAt,
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.validateRefreshToken(presentedToken);

      // Roles should be a copy
      expect(result.roles).toEqual(storedRoles);
      expect(result.roles).not.toBe(storedRoles);
    });
  });

  describe('storeRefreshToken', () => {
    it('stores token with calculated expiry', async () => {
      const accountId = 1;
      const userId = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
      const roles = ['CUSTOMER'];
      const tokenHash = 'abc123hash';
      const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';

      repository.create.mockResolvedValue({
        id: 100,
        accountId,
        userId,
        roles,
        tokenHash,
        familyId,
        expiresAt: new Date(),
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      const result = await service.storeRefreshToken(accountId, userId, roles, tokenHash, familyId);

      expect(result).toBe(100);
      expect(repository.create).toHaveBeenCalled();

      const call = (repository.create as jest.Mock).mock.calls[0];
      expect(call[0]).toBe(accountId);
      expect(call[1]).toBe(userId);
      expect(call[2]).toEqual(roles);
      expect(call[3]).toBe(tokenHash);
      expect(call[4]).toBe(familyId);
      // call[5] is expiresAt - we check it's about 7 days from now
      const expiresAt = call[5] as Date;
      const now = new Date();
      const diffMs = expiresAt.getTime() - now.getTime();
      const diffDays = diffMs / (1000 * 60 * 60 * 24);
      expect(diffDays).toBeCloseTo(7, 0); // Approximately 7 days
    });
  });

  describe('consumeRefreshToken', () => {
    it('calls repository consume', async () => {
      const tokenId = 42;
      const mockRecord = {
        id: tokenId,
        accountId: 1,
        userId: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        roles: ['CUSTOMER'],
        tokenHash: 'abc123',
        familyId: 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee',
        expiresAt: new Date(),
        consumedAt: new Date(),
        revokedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      repository.consume.mockResolvedValue(mockRecord);

      const result = await service.consumeRefreshToken(tokenId);

      expect(result).toEqual(mockRecord);
      expect(repository.consume).toHaveBeenCalledWith(tokenId);
    });
  });

  describe('revokeFamily', () => {
    it('revokes entire family and logs', async () => {
      const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';
      repository.revokeFamily.mockResolvedValue(3); // 3 tokens revoked

      await service.revokeFamily(familyId);

      expect(repository.revokeFamily).toHaveBeenCalledWith(familyId);
      expect(logger.warn).toHaveBeenCalledWith(
        'revoked refresh token family due to reuse',
        expect.objectContaining({ familyId }),
      );
    });
  });

  describe('generateFamilyId', () => {
    it('generates a UUID-like family ID', () => {
      const familyId = service.generateFamilyId();

      // Should be UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
      expect(familyId).toMatch(
        /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/,
      );
    });

    it('generates different IDs on each call', () => {
      const id1 = service.generateFamilyId();
      const id2 = service.generateFamilyId();

      expect(id1).not.toBe(id2);
    });
  });
});
