import { ConfigService } from '@nestjs/config';
import { RefreshTokensRepository } from './refresh-tokens.repository';
import { RedactingLogger } from '../logging/redacting-logger.service';
import { Pool } from 'pg';

jest.mock('pg');

describe('RefreshTokensRepository', () => {
  let repository: RefreshTokensRepository;
  let mockPool: any;
  let mockConfigService: any;
  let mockLogger: any;

  const mockTokenRecord = {
    id: 1,
    account_id: 123,
    user_id: 'user-uuid-123',
    roles: ['admin', 'user'],
    token_hash: 'hash-abc123',
    family_id: 'family-uuid-456',
    expires_at: '2026-12-31T23:59:59Z',
    consumed_at: null,
    revoked_at: null,
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    mockLogger = {
      error: jest.fn(),
      warn: jest.fn(),
      log: jest.fn(),
    };

    mockConfigService = {
      get: jest.fn((key: string) => {
        const values: Record<string, any> = {
          POSTGRES_HOST: 'localhost',
          POSTGRES_PORT: 5432,
          POSTGRES_DB: 'test_db',
          POSTGRES_USER: 'test_user',
          POSTGRES_PASSWORD: 'test_password',
        };
        return values[key];
      }),
    };

    mockPool = {
      query: jest.fn(),
    };

    (Pool as jest.MockedClass<typeof Pool>).mockImplementation(() => mockPool);

    repository = new RefreshTokensRepository(mockConfigService, mockLogger);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('constructor', () => {
    it('should initialize pool with config values', () => {
      expect(Pool).toHaveBeenCalledWith({
        host: 'localhost',
        port: 5432,
        database: 'test_db',
        user: 'test_user',
        password: 'test_password',
      });
    });

    it('should call configService.get for each required config', () => {
      expect(mockConfigService.get).toHaveBeenCalledWith('POSTGRES_HOST', { infer: true });
      expect(mockConfigService.get).toHaveBeenCalledWith('POSTGRES_PORT', { infer: true });
      expect(mockConfigService.get).toHaveBeenCalledWith('POSTGRES_DB', { infer: true });
      expect(mockConfigService.get).toHaveBeenCalledWith('POSTGRES_USER', { infer: true });
      expect(mockConfigService.get).toHaveBeenCalledWith('POSTGRES_PASSWORD', { infer: true });
    });
  });

  describe('create', () => {
    it('should successfully create a refresh token', async () => {
      mockPool.query.mockResolvedValue({ rows: [mockTokenRecord] });

      const result = await repository.create(
        123,
        'user-uuid-123',
        ['admin', 'user'],
        'hash-abc123',
        'family-uuid-456',
        new Date('2026-12-31T23:59:59Z'),
      );

      expect(result).toEqual({
        id: 1,
        accountId: 123,
        userId: 'user-uuid-123',
        roles: ['admin', 'user'],
        tokenHash: 'hash-abc123',
        familyId: 'family-uuid-456',
        expiresAt: new Date('2026-12-31T23:59:59Z'),
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
      });

      expect(mockPool.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO auth.refresh_tokens'),
        [123, 'user-uuid-123', ['admin', 'user'], 'hash-abc123', 'family-uuid-456', expect.any(Date)],
      );
    });

    it('should throw error when insert returns no rows', async () => {
      mockPool.query.mockResolvedValue({ rows: [] });

      await expect(
        repository.create(
          123,
          'user-uuid-123',
          ['admin', 'user'],
          'hash-abc123',
          'family-uuid-456',
          new Date('2026-12-31T23:59:59Z'),
        ),
      ).rejects.toThrow('Failed to create refresh token');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to create refresh token',
        expect.objectContaining({ accountId: 123 }),
      );
    });

    it('should handle database errors during create', async () => {
      const error = new Error('Database connection failed');
      mockPool.query.mockRejectedValue(error);

      await expect(
        repository.create(
          123,
          'user-uuid-123',
          ['admin', 'user'],
          'hash-abc123',
          'family-uuid-456',
          new Date('2026-12-31T23:59:59Z'),
        ),
      ).rejects.toThrow('Database connection failed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to create refresh token',
        expect.objectContaining({
          accountId: 123,
          errorMessage: 'Database connection failed',
        }),
      );
    });

    it('should handle non-Error exceptions during create', async () => {
      mockPool.query.mockRejectedValue('string error');

      await expect(
        repository.create(
          123,
          'user-uuid-123',
          ['admin', 'user'],
          'hash-abc123',
          'family-uuid-456',
          new Date('2026-12-31T23:59:59Z'),
        ),
      ).rejects.toBe('string error');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to create refresh token',
        expect.objectContaining({ errorMessage: 'unknown error' }),
      );
    });
  });

  describe('findByHash', () => {
    it('should find token by hash', async () => {
      mockPool.query.mockResolvedValue({ rows: [mockTokenRecord] });

      const result = await repository.findByHash('hash-abc123');

      expect(result).toEqual({
        id: 1,
        accountId: 123,
        userId: 'user-uuid-123',
        roles: ['admin', 'user'],
        tokenHash: 'hash-abc123',
        familyId: 'family-uuid-456',
        expiresAt: new Date('2026-12-31T23:59:59Z'),
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
      });

      expect(mockPool.query).toHaveBeenCalledWith(
        'SELECT * FROM auth.refresh_tokens WHERE token_hash = $1',
        ['hash-abc123'],
      );
    });

    it('should return null when token not found', async () => {
      mockPool.query.mockResolvedValue({ rows: [] });

      const result = await repository.findByHash('nonexistent-hash');

      expect(result).toBeNull();
    });

    it('should handle database errors during findByHash', async () => {
      const error = new Error('Database connection failed');
      mockPool.query.mockRejectedValue(error);

      await expect(repository.findByHash('hash-abc123')).rejects.toThrow('Database connection failed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to find refresh token by hash',
        expect.objectContaining({ errorMessage: 'Database connection failed' }),
      );
    });
  });

  describe('consume', () => {
    it('should mark token as consumed', async () => {
      const consumedRecord = {
        ...mockTokenRecord,
        consumed_at: '2026-01-02T00:00:00Z',
        updated_at: '2026-01-02T00:00:00Z',
      };
      mockPool.query.mockResolvedValue({ rows: [consumedRecord] });

      const result = await repository.consume(1);

      expect(result).toEqual({
        id: 1,
        accountId: 123,
        userId: 'user-uuid-123',
        roles: ['admin', 'user'],
        tokenHash: 'hash-abc123',
        familyId: 'family-uuid-456',
        expiresAt: new Date('2026-12-31T23:59:59Z'),
        consumedAt: new Date('2026-01-02T00:00:00Z'),
        revokedAt: null,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-02T00:00:00Z'),
      });

      expect(mockPool.query).toHaveBeenCalledWith(
        expect.stringContaining('UPDATE auth.refresh_tokens SET consumed_at'),
        [1],
      );
    });

    it('should throw error when token not found or already consumed', async () => {
      mockPool.query.mockResolvedValue({ rows: [] });

      await expect(repository.consume(999)).rejects.toThrow('Token not found or already consumed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to consume refresh token',
        expect.objectContaining({ tokenId: 999 }),
      );
    });

    it('should handle database errors during consume', async () => {
      const error = new Error('Database connection failed');
      mockPool.query.mockRejectedValue(error);

      await expect(repository.consume(1)).rejects.toThrow('Database connection failed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to consume refresh token',
        expect.objectContaining({ tokenId: 1, errorMessage: 'Database connection failed' }),
      );
    });
  });

  describe('revokeFamily', () => {
    it('should revoke all tokens in a family', async () => {
      mockPool.query.mockResolvedValue({ rowCount: 3 });

      const result = await repository.revokeFamily('family-uuid-456');

      expect(result).toBe(3);
      expect(mockPool.query).toHaveBeenCalledWith(
        expect.stringContaining('UPDATE auth.refresh_tokens SET revoked_at'),
        ['family-uuid-456'],
      );
    });

    it('should return 0 when no tokens found to revoke', async () => {
      mockPool.query.mockResolvedValue({ rowCount: 0 });

      const result = await repository.revokeFamily('nonexistent-family');

      expect(result).toBe(0);
    });

    it('should handle rowCount being null', async () => {
      mockPool.query.mockResolvedValue({ rowCount: null });

      const result = await repository.revokeFamily('family-uuid-456');

      expect(result).toBe(0);
    });

    it('should handle database errors during revokeFamily', async () => {
      const error = new Error('Database connection failed');
      mockPool.query.mockRejectedValue(error);

      await expect(repository.revokeFamily('family-uuid-456')).rejects.toThrow('Database connection failed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to revoke refresh token family',
        expect.objectContaining({
          familyId: 'family-uuid-456',
          errorMessage: 'Database connection failed',
        }),
      );
    });
  });

  describe('findLiveTokensForAccount', () => {
    it('should find all live tokens for an account', async () => {
      const mockRecords = [mockTokenRecord, { ...mockTokenRecord, id: 2, family_id: 'family-uuid-789' }];
      mockPool.query.mockResolvedValue({ rows: mockRecords });

      const result = await repository.findLiveTokensForAccount(123);

      expect(result).toHaveLength(2);
      expect(result[0]).toEqual({
        id: 1,
        accountId: 123,
        userId: 'user-uuid-123',
        roles: ['admin', 'user'],
        tokenHash: 'hash-abc123',
        familyId: 'family-uuid-456',
        expiresAt: new Date('2026-12-31T23:59:59Z'),
        consumedAt: null,
        revokedAt: null,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
      });

      expect(mockPool.query).toHaveBeenCalledWith(
        expect.stringContaining('SELECT * FROM auth.refresh_tokens'),
        [123],
      );
    });

    it('should return empty array when no live tokens found', async () => {
      mockPool.query.mockResolvedValue({ rows: [] });

      const result = await repository.findLiveTokensForAccount(999);

      expect(result).toEqual([]);
    });

    it('should handle database errors during findLiveTokensForAccount', async () => {
      const error = new Error('Database connection failed');
      mockPool.query.mockRejectedValue(error);

      await expect(repository.findLiveTokensForAccount(123)).rejects.toThrow('Database connection failed');

      expect(mockLogger.error).toHaveBeenCalledWith(
        'failed to find live refresh tokens for account',
        expect.objectContaining({
          accountId: 123,
          errorMessage: 'Database connection failed',
        }),
      );
    });
  });

  describe('mapRow (private method testing via public methods)', () => {
    it('should correctly map database row to RefreshTokenRecord', async () => {
      const dbRow = {
        id: 5,
        account_id: 456,
        user_id: 'user-xyz',
        roles: ['viewer'],
        token_hash: 'hash-xyz',
        family_id: 'family-xyz',
        expires_at: '2025-06-15T10:30:00Z',
        consumed_at: '2025-06-14T10:30:00Z',
        revoked_at: null,
        created_at: '2025-06-10T00:00:00Z',
        updated_at: '2025-06-14T10:30:00Z',
      };

      mockPool.query.mockResolvedValue({ rows: [dbRow] });

      const result = await repository.findByHash('hash-xyz');

      expect(result).toEqual({
        id: 5,
        accountId: 456,
        userId: 'user-xyz',
        roles: ['viewer'],
        tokenHash: 'hash-xyz',
        familyId: 'family-xyz',
        expiresAt: new Date('2025-06-15T10:30:00Z'),
        consumedAt: new Date('2025-06-14T10:30:00Z'),
        revokedAt: null,
        createdAt: new Date('2025-06-10T00:00:00Z'),
        updatedAt: new Date('2025-06-14T10:30:00Z'),
      });
    });

    it('should handle null dates in mapping', async () => {
      const dbRow = {
        ...mockTokenRecord,
        consumed_at: null,
        revoked_at: null,
      };

      mockPool.query.mockResolvedValue({ rows: [dbRow] });

      const result = await repository.findByHash('hash-abc123');

      expect(result?.consumedAt).toBeNull();
      expect(result?.revokedAt).toBeNull();
    });
  });
});
