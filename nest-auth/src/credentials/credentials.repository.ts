import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';
import { EnvConfig } from '../config/env.validation';
import { RedactingLogger } from '../logging/redacting-logger.service';
import { CredentialRecord } from './credential-record';

@Injectable()
export class CredentialsRepository {
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

  async findByAccountId(accountId: number): Promise<CredentialRecord | null> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        username: string;
        roles: 'USER' | 'ADMIN';
        password_hash: string;
        created_at: string;
      }>('SELECT id, account_id, username, roles, password_hash, created_at FROM auth.credentials WHERE account_id = $1', [
        accountId,
      ]);

      if (result.rows.length === 0) {
        return null;
      }

      const row = result.rows[0];
      return {
        // pg returns BIGINT columns as strings by default (they can exceed
        // Number.MAX_SAFE_INTEGER in general); account ids in this domain
        // never do, so converting here keeps the runtime value honest about
        // the `number` type CredentialRecord declares.
        id: Number(row.id),
        accountId: Number(row.account_id),
        username: row.username,
        role: row.roles,
        passwordHash: row.password_hash,
        createdOn: new Date(row.created_at),
      };
    } catch (error) {
      // Log only the lookup key, never the raw error object or a whole
      // request/DTO - a pg error can echo query parameters, and this keeps
      // that possibility out of the log call entirely rather than relying
      // on the redactor as the only safeguard.
      this.logger.error('credentials lookup failed', {
        accountId,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  async findByUsername(username: string): Promise<CredentialRecord | null> {
    try {
      const result = await this.pool.query<{
        id: number;
        account_id: number;
        username: string;
        roles: 'USER' | 'ADMIN';
        password_hash: string;
        created_at: string;
      }>('SELECT id, account_id, username, roles, password_hash, created_at FROM auth.credentials WHERE username = $1', [
        username,
      ]);

      if (result.rows.length === 0) {
        return null;
      }

      const row = result.rows[0];
      return {
        id: Number(row.id),
        accountId: Number(row.account_id),
        username: row.username,
        role: row.roles,
        passwordHash: row.password_hash,
        createdOn: new Date(row.created_at),
      };
    } catch (error) {
      this.logger.error('credentials lookup by username failed', {
        username,
        errorMessage: error instanceof Error ? error.message : 'unknown error',
      });
      throw error;
    }
  }

  async createCredential(
    accountId: number,
    username: string,
    passwordHash: string,
    role: 'USER' | 'ADMIN',
  ): Promise<CredentialRecord> {
    const result = await this.pool.query<{
      id: number;
      account_id: number;
      username: string;
      roles: 'USER' | 'ADMIN';
      password_hash: string;
      created_at: string;
    }>(
      `INSERT INTO auth.credentials (account_id, username, roles, password_hash)
       VALUES ($1, $2, $3, $4)
       RETURNING id, account_id, username, roles, password_hash, created_at`,
      [accountId, username, role, passwordHash],
    );

    const row = result.rows[0];
    return {
      id: Number(row.id),
      accountId: Number(row.account_id),
      username: row.username,
      role: row.roles,
      passwordHash: row.password_hash,
      createdOn: new Date(row.created_at),
    };
  }
}
