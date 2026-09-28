import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt, { JwtPayload } from 'jsonwebtoken';
import { EnvConfig } from '../../config/env.validation';

export interface AccessTokenSubject {
  sub: string;
  accountId: number;
  roles: string[];
}

export interface VerifiedAccessTokenPayload extends AccessTokenSubject {
  iat: number;
  exp: number;
  iss: string;
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

  verifyAccessToken(token: string): VerifiedAccessTokenPayload {
    const jwtSecret = this.configService.get('JWT_SECRET', { infer: true });
    const jwtIssuer = this.configService.get('JWT_ISSUER', { infer: true });

    const verified = jwt.verify(token, jwtSecret, {
      algorithms: ['HS256'],
      issuer: jwtIssuer,
    });

    if (typeof verified === 'string') {
      throw new Error('Invalid token payload type');
    }

    return this.toVerifiedPayload(verified, jwtIssuer);
  }

  private toVerifiedPayload(
    payload: JwtPayload,
    expectedIssuer: string,
  ): VerifiedAccessTokenPayload {
    const payloadKeys = Object.keys(payload).sort();
    const contractKeys = ['accountId', 'exp', 'iat', 'iss', 'roles', 'sub'].sort();
    if (payloadKeys.length !== contractKeys.length) {
      throw new Error('Token payload keys do not match contract');
    }

    for (let i = 0; i < contractKeys.length; i += 1) {
      if (payloadKeys[i] !== contractKeys[i]) {
        throw new Error('Token payload keys do not match contract');
      }
    }

    if (typeof payload.sub !== 'string') {
      throw new Error('Invalid sub claim');
    }

    if (typeof payload.accountId !== 'number' || !Number.isInteger(payload.accountId)) {
      throw new Error('Invalid accountId claim');
    }

    if (!Array.isArray(payload.roles) || payload.roles.length === 0) {
      throw new Error('Invalid roles claim');
    }

    if (payload.roles.some((role) => typeof role !== 'string' || role.length === 0)) {
      throw new Error('Invalid roles claim');
    }

    if (typeof payload.iat !== 'number' || !Number.isInteger(payload.iat)) {
      throw new Error('Invalid iat claim');
    }

    if (typeof payload.exp !== 'number' || !Number.isInteger(payload.exp)) {
      throw new Error('Invalid exp claim');
    }

    if (typeof payload.iss !== 'string' || payload.iss !== expectedIssuer) {
      throw new Error('Invalid iss claim');
    }

    return {
      sub: payload.sub,
      accountId: payload.accountId,
      roles: [...payload.roles],
      iat: payload.iat,
      exp: payload.exp,
      iss: payload.iss,
    };
  }
}