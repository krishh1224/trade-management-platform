import { HttpException, HttpStatus, Injectable } from '@nestjs/common';
import { createHash } from 'crypto';
import { AuthTokenService, VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';
import { RefreshTokenService } from '../auth-token/refresh-token.service';
import { CredentialsRepository } from '../credentials/credentials.repository';
import { PasswordService } from '../credentials/password.service';
import { LoginRequest, RegisterRequest, UserResponse } from './auth.dto';
import { TokenResponse } from './refresh.dto';

const DEFAULT_ROLE: 'USER' | 'ADMIN' = 'USER';

@Injectable()
export class AuthService {
  constructor(
    private readonly credentialsRepository: CredentialsRepository,
    private readonly passwordService: PasswordService,
    private readonly authTokenService: AuthTokenService,
    private readonly refreshTokenService: RefreshTokenService,
  ) {}

  async register(request: RegisterRequest): Promise<UserResponse> {
    const passwordHash = await this.passwordService.hash(request.password);

    try {
      const created = await this.credentialsRepository.createCredential(
        request.accountId,
        request.username,
        passwordHash,
        DEFAULT_ROLE,
      );

      return {
        id: this.subjectForAccount(created.accountId),
        username: created.username,
        accountId: created.accountId,
        roles: [created.role],
        createdOn: created.createdOn.toISOString(),
      };
    } catch (error) {
      if (this.isUniqueViolation(error)) {
        throw new HttpException(
          { errorCode: 'AUTH-409', message: 'Registration failed' },
          HttpStatus.CONFLICT,
        );
      }
      throw error;
    }
  }

  async login(request: LoginRequest): Promise<TokenResponse> {
    const credential = await this.credentialsRepository.findByUsername(request.username);
    if (!credential) {
      throw this.unauthorised();
    }

    const passwordMatches = await this.passwordService.verify(request.password, credential.passwordHash);
    if (!passwordMatches) {
      throw this.unauthorised();
    }

    const subject = {
      sub: this.subjectForAccount(credential.accountId),
      accountId: credential.accountId,
      roles: [credential.role],
    };

    const accessToken = this.authTokenService.issueAccessToken(subject);
    const familyId = this.refreshTokenService.generateFamilyId();
    const issued = this.refreshTokenService.issueRefreshToken(familyId);
    await this.refreshTokenService.storeRefreshToken(
      credential.accountId,
      subject.sub,
      [credential.role],
      issued.tokenHash,
      familyId,
    );

    return {
      accessToken,
      refreshToken: issued.refreshToken,
      tokenType: 'Bearer',
      expiresIn: 900,
    };
  }

  async refresh(refreshToken: string): Promise<TokenResponse> {
    try {
      const validation = await this.refreshTokenService.validateRefreshToken(refreshToken);

      if (validation.isExpired || validation.isRevoked) {
        throw this.unauthorised();
      }

      if (validation.isConsumed) {
        await this.refreshTokenService.revokeFamily(validation.familyId);
        throw this.unauthorised();
      }

      const subject = {
        sub: validation.userId,
        accountId: validation.accountId,
        roles: [...validation.roles],
      };

      const newAccessToken = this.authTokenService.issueAccessToken(subject);
      const newFamilyId = this.refreshTokenService.generateFamilyId();
      const newRefreshTokenIssue = this.refreshTokenService.issueRefreshToken(newFamilyId);

      await this.refreshTokenService.consumeRefreshToken(validation.tokenId);
      await this.refreshTokenService.storeRefreshToken(
        validation.accountId,
        validation.userId,
        validation.roles,
        newRefreshTokenIssue.tokenHash,
        newFamilyId,
      );

      return {
        accessToken: newAccessToken,
        refreshToken: newRefreshTokenIssue.refreshToken,
        tokenType: 'Bearer',
        expiresIn: 900,
      };
    } catch (error) {
      if (error instanceof HttpException) {
        throw error;
      }
      throw this.unauthorised();
    }
  }

  async me(currentUser: VerifiedAccessTokenPayload): Promise<UserResponse> {
    const credential = await this.credentialsRepository.findByAccountId(currentUser.accountId);
    if (!credential) {
      throw this.unauthorised();
    }

    return {
      id: currentUser.sub,
      username: credential.username,
      accountId: credential.accountId,
      roles: [...currentUser.roles],
      createdOn: credential.createdOn.toISOString(),
    };
  }

  private unauthorised(): HttpException {
    return new HttpException(
      { errorCode: 'AUTH-401', message: 'Unauthorised' },
      HttpStatus.UNAUTHORIZED,
    );
  }

  private isUniqueViolation(error: unknown): boolean {
    if (!error || typeof error !== 'object') {
      return false;
    }

    const pgError = error as { code?: string };
    return pgError.code === '23505';
  }

  private subjectForAccount(accountId: number): string {
    const hex = createHash('sha256').update(`account:${accountId}`).digest('hex');
    const p1 = hex.slice(0, 8);
    const p2 = hex.slice(8, 12);
    const p3 = `4${hex.slice(13, 16)}`;
    const p4 = `a${hex.slice(17, 20)}`;
    const p5 = hex.slice(20, 32);
    return `${p1}-${p2}-${p3}-${p4}-${p5}`;
  }
}
