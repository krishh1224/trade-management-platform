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
        password_hash: string;
      }>('SELECT id, account_id, password_hash FROM credentials WHERE account_id = $1', [
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
        passwordHash: row.password_hash,
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
}
