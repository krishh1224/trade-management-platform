import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';
import { EnvConfig } from '../config/env.validation';

export interface HealthStatus {
  status: 'UP' | 'DOWN';
  postgresReachable: boolean;
}

@Injectable()
export class HealthService {
  private readonly pool: Pool;

  constructor(configService: ConfigService<EnvConfig, true>) {
    this.pool = new Pool({
      host: configService.get('POSTGRES_HOST', { infer: true }),
      port: configService.get('POSTGRES_PORT', { infer: true }),
      database: configService.get('POSTGRES_DB', { infer: true }),
      user: configService.get('POSTGRES_USER', { infer: true }),
      password: configService.get('POSTGRES_PASSWORD', { infer: true }),
      connectionTimeoutMillis: 2000,
    });
  }

  async check(): Promise<HealthStatus> {
    const postgresReachable = await this.pingPostgres();
    return {
      status: postgresReachable ? 'UP' : 'DOWN',
      postgresReachable,
    };
  }

  private async pingPostgres(): Promise<boolean> {
    try {
      await this.pool.query('SELECT 1');
      return true;
    } catch {
      return false;
    }
  }
}
