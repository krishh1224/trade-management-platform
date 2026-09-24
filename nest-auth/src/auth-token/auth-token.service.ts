import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt from 'jsonwebtoken';
import { EnvConfig } from '../config/env.validation';

export interface AccessTokenSubject {
  sub: string;
  accountId: number;
  roles: string[];
}

@Injectable()
export class AuthTokenService {
  constructor(private readonly configService: ConfigService<EnvConfig, true>) {}

  issueAccessToken(subject: AccessTokenSubject): string {
    const payload = {
      sub: subject.sub,
      accountId: subject.accountId,
      roles: [...subject.roles],
    };

    const jwtSecret = this.configService.get('JWT_SECRET', { infer: true });
    const jwtIssuer = this.configService.get('JWT_ISSUER', { infer: true });
    const accessTokenTtlSeconds = this.configService.get('ACCESS_TOKEN_TTL_SECONDS', {
      infer: true,
    });

    return jwt.sign(payload, jwtSecret, {
      algorithm: 'HS256',
      issuer: jwtIssuer,
      expiresIn: accessTokenTtlSeconds,
    });
  }
}