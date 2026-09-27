import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { randomBytes, createHash } from 'crypto';
import { EnvConfig } from '../../config/env.validation';
import { RedactingLogger } from '../../logging/redacting-logger.service';
import { RefreshTokensRepository } from '../../credentials/refresh-tokens.repository';
import { AccessTokenSubject } from './auth-token.service';

export interface RefreshTokenIssueResult {
  refreshToken: string; // Opaque, random, never logged
  tokenHash: string; // Hash stored in DB
  familyId: string; // UUID tracking the token chain
}

export interface RefreshTokenValidationResult {
  accountId: number;
  userId: string;
  roles: string[];
  tokenId: number;
  tokenHash: string;
  familyId: string;
  isConsumed: boolean;
  isRevoked: boolean;
  isExpired: boolean;
}

@Injectable()
export class RefreshTokenService {
  // 256-bit (32 bytes) of random data, hex-encoded = 64 characters
  private readonly TOKEN_LENGTH_BYTES = 32;

  constructor(
    private readonly refreshTokensRepository: RefreshTokensRepository,
    private readonly configService: ConfigService<EnvConfig, true>,
    private readonly logger: RedactingLogger,
  ) {}

  /**
   * Generate a new opaque refresh token and its hash.
   * The token is returned to the client; the hash is stored.
   */
  issueRefreshToken(familyId: string): RefreshTokenIssueResult {
    // Generate 256 bits of random data
    const tokenBuffer = randomBytes(this.TOKEN_LENGTH_BYTES);
    const refreshToken = tokenBuffer.toString('hex');

    // Hash the token for storage
    const tokenHash = this.hashToken(refreshToken);

    return {
      refreshToken,
      tokenHash,
      familyId,
    };
  }

  /**
   * Validate a presented refresh token:
   * 1. Look it up by hash in the database
   * 2. Check if it's expired, consumed, or revoked
   * 3. Return validation result (caller decides what to do)
   */
  async validateRefreshToken(presentedToken: string): Promise<RefreshTokenValidationResult> {
    const tokenHash = this.hashToken(presentedToken);

    const record = await this.refreshTokensRepository.findByHash(tokenHash);

    if (!record) {
      // Token not found in database
      throw new Error('Invalid refresh token');
    }

    const now = new Date();
    const isExpired = record.expiresAt < now;
    const isConsumed = record.consumedAt !== null;
    const isRevoked = record.revokedAt !== null;

    return {
      accountId: record.accountId,
      userId: record.userId,
      roles: [...record.roles],
      tokenId: record.id,
      tokenHash: record.tokenHash,
      familyId: record.familyId,
      isConsumed,
      isRevoked,
      isExpired,
    };
  }

  /**
   * Store a new refresh token in the database.
   * Called after issuing a new token (on login or refresh).
   */
  async storeRefreshToken(
    accountId: number,
    userId: string,
    roles: string[],
    tokenHash: string,
    familyId: string,
  ): Promise<number> {
    const refreshTokenTtlDays = this.configService.get('REFRESH_TOKEN_TTL_DAYS', { infer: true });
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + refreshTokenTtlDays);

    const record = await this.refreshTokensRepository.create(
      accountId,
      userId,
      roles,
      tokenHash,
      familyId,
      expiresAt,
    );

    return record.id;
  }

  /**
   * Consume a refresh token (mark it as used).
   * Should be done atomically with issuing a new token.
   * Returns the consumed record.
   */
  async consumeRefreshToken(tokenId: number) {
    return await this.refreshTokensRepository.consume(tokenId);
  }

  /**
   * Revoke the entire family of refresh tokens.
   * Called when a consumed token is presented again (theft detection).
   */
  async revokeFamily(familyId: string): Promise<void> {
    const revokedCount = await this.refreshTokensRepository.revokeFamily(familyId);
    this.logger.warn('revoked refresh token family due to reuse', {
      familyId,
      revokedCount,
    });
  }

  /**
   * Generate a new family ID (UUID v4-like random) for a new token chain.
   * Used on login (first token) and when starting a new chain after revocation.
   */
  generateFamilyId(): string {
    // Generate a UUID v4-like identifier (not a true v4, just random enough for this use)
    return this.uuidv4Like();
  }

  private hashToken(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }

  private uuidv4Like(): string {
    const bytes = randomBytes(16);
    bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
    bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 1

    const hex = bytes.toString('hex');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
}
