import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';
import { EnvConfig } from '../config/env.validation';
import { RedactingLogger } from '../logging/redacting-logger.service';
import { RefreshTokenRecord } from './refresh-token-record';

@Injectable()
export class RefreshTokensRepository {
  private readonly pool: Pool;

  constructor(
    configService: ConfigService<EnvConfig, true>,
    private readonly logger: RedactingLogger,
  ) {
    this.pool = new Pool({
      host: configService.get('POSTGRES_HOST', { infer: true }),
      port: configService.get('POSTGRES_PORT', { infer: true }),
      database: configService.get('POSTGRES_DB', { infer: true }),
      user: configService.get('POSTGRES_USER', { infer: true }),
      password: configService.get('POSTGRES_PASSWORD', { infer: true }),
    });
  }

  /**
   * Store a new refresh token.
   * Returns the token record (without the raw token, only the hash).
   */
  async create(
    accountId: number,
    userId: string,
    roles: string[],
    tokenHash: string,
    familyId: string,
    expiresAt: Date,
  ): Promise<RefreshTokenRecord> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        user_id: string;
        roles: string[];
        token_hash: string;
        family_id: string;
        expires_at: string;
        consumed_at: string | null;
        revoked_at: string | null;
        created_at: string;
        updated_at: string;
      }>(
        `INSERT INTO auth.refresh_tokens (account_id, user_id, roles, token_hash, family_id, expires_at)
         VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING id, account_id, user_id, roles, token_hash, family_id, expires_at, consumed_at, revoked_at, created_at, updated_at`,
        [accountId, userId, roles, tokenHash, familyId, expiresAt],
      );

      if (result.rows.length === 0) {
        throw new Error('Failed to create refresh token');
      }

      return this.mapRow(result.rows[0]);
    } catch (error) {
      this.logger.error('failed to create refresh token', {
        accountId,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  /**
   * Find a refresh token by its hash (for validation/consumption).
   * Returns null if not found, the record if found.
   */
  async findByHash(tokenHash: string): Promise<RefreshTokenRecord | null> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        user_id: string;
        roles: string[];
        token_hash: string;
        family_id: string;
        expires_at: string;
        consumed_at: string | null;
        revoked_at: string | null;
        created_at: string;
        updated_at: string;
      }>('SELECT * FROM auth.refresh_tokens WHERE token_hash = $1', [tokenHash]);

      if (result.rows.length === 0) {
        return null;
      }

      return this.mapRow(result.rows[0]);
    } catch (error) {
      this.logger.error('failed to find refresh token by hash', {
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  /**
   * Mark a token as consumed and return the updated record.
   * This should be part of a transaction with issueNew to ensure atomicity.
   */
  async consume(tokenId: number): Promise<RefreshTokenRecord> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        user_id: string;
        roles: string[];
        token_hash: string;
        family_id: string;
        expires_at: string;
        consumed_at: string | null;
        revoked_at: string | null;
        created_at: string;
        updated_at: string;
      }>(
        `UPDATE auth.refresh_tokens SET consumed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
         WHERE id = $1 AND consumed_at IS NULL
         RETURNING id, account_id, user_id, roles, token_hash, family_id, expires_at, consumed_at, revoked_at, created_at, updated_at`,
        [tokenId],
      );

      if (result.rows.length === 0) {
        throw new Error('Token not found or already consumed');
      }

      return this.mapRow(result.rows[0]);
    } catch (error) {
      this.logger.error('failed to consume refresh token', {
        tokenId,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  /**
   * Revoke all tokens in a family (theft detection).
   * Called when a consumed token is presented again.
   */
  async revokeFamily(familyId: string): Promise<number> {
    try {
      const result = await this.pool.query(
        `UPDATE auth.refresh_tokens SET revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
         WHERE family_id = $1 AND revoked_at IS NULL`,
        [familyId],
      );

      return result.rowCount ?? 0;
    } catch (error) {
      this.logger.error('failed to revoke refresh token family', {
        familyId,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  /**
   * Find all live (not consumed, not revoked) tokens for an account.
   * Used for informational checks, not part of the core refresh flow.
   */
  async findLiveTokensForAccount(accountId: number): Promise<RefreshTokenRecord[]> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        user_id: string;
        roles: string[];
        token_hash: string;
        family_id: string;
        expires_at: string;
        consumed_at: string | null;
        revoked_at: string | null;
        created_at: string;
        updated_at: string;
      }>(
        `SELECT * FROM auth.refresh_tokens
         WHERE account_id = $1 AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
         ORDER BY created_at DESC`,
        [accountId],
      );

      return result.rows.map((row) => this.mapRow(row));
    } catch (error) {
      this.logger.error('failed to find live refresh tokens for account', {
        accountId,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  private mapRow(row: {
    id: number;
    account_id: number;
    user_id: string;
    roles: string[];
    token_hash: string;
    family_id: string;
    expires_at: string;
    consumed_at: string | null;
    revoked_at: string | null;
    created_at: string;
    updated_at: string;
  }): RefreshTokenRecord {
    return {
      id: Number(row.id),
      accountId: Number(row.account_id),
      userId: row.user_id,
      roles: [...row.roles],
      tokenHash: row.token_hash,
      familyId: row.family_id,
      expiresAt: new Date(row.expires_at),
      consumedAt: row.consumed_at ? new Date(row.consumed_at) : null,
      revokedAt: row.revoked_at ? new Date(row.revoked_at) : null,
      createdAt: new Date(row.created_at),
      updatedAt: new Date(row.updated_at),
    };
  }
}
