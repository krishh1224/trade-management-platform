import { HttpException, HttpStatus, Injectable } from '@nestjs/common';
import { createHash } from 'crypto';
import { Request } from 'express';
import { AuthTokenService, VerifiedAccessTokenPayload } from '../../token/services/auth-token.service';
import { RefreshTokenService } from '../../token/services/refresh-token.service';
import { CredentialsRepository } from '../../credentials/credentials.repository';
import { PasswordService } from '../../credentials/password.service';
import {
  ChangePasswordRequest,
  LoginRequest,
  PasswordUpdateResponse,
  RegisterRequest,
  UserResponse,
} from '../dto/auth.dto';
import { TokenResponse } from '../dto/refresh.dto';
import { LoginThrottleService } from './login-throttle.service';

const DEFAULT_ROLE: 'USER' | 'ADMIN' = 'USER';

/**
 * Dummy password hash for timing-safe unknown username checks.
 * Generated via bcrypt.hash('dummy', 12) and stored as a constant.
 * When a username is not found, we verify against this hash to ensure
 * the password-hashing work is performed, preventing timing attacks
 * that could reveal whether a username exists.
 *
 * The hash value itself is never used; the result of comparison is discarded.
 * Any bcrypt hash will work; this one is arbitrary.
 */
const DUMMY_PASSWORD_HASH =
  '$2b$12$PjZlWHphNX1z8nKZR3zYu.KZJ8kZcOIWIbVbWZwFzC5CvNgxEhMqm';

@Injectable()
export class AuthService {
  constructor(
    private readonly credentialsRepository: CredentialsRepository,
    private readonly passwordService: PasswordService,
    private readonly authTokenService: AuthTokenService,
    private readonly refreshTokenService: RefreshTokenService,
    private readonly loginThrottleService: LoginThrottleService,
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
      if (this.isForeignKeyViolation(error)) {
        throw new HttpException(
          { errorCode: 'VAL-422', message: 'Invalid input' },
          HttpStatus.UNPROCESSABLE_ENTITY,
        );
      }
      throw error;
    }
  }

  async login(request: LoginRequest, callerIp: string): Promise<TokenResponse> {
    // Check if caller is throttled before attempting login
    this.loginThrottleService.checkThrottle(callerIp);

    try {
      const credential = await this.credentialsRepository.findByUsername(request.username);

      // Timing-safe unknown username check: always verify password to ensure comparable work
      const passwordHash = credential?.passwordHash ?? DUMMY_PASSWORD_HASH;
      const passwordMatches = await this.passwordService.verify(request.password, passwordHash);

      // Reject if username not found OR password wrong
      if (!credential || !passwordMatches) {
        this.loginThrottleService.recordFailure(callerIp);
        throw this.unauthorised();
      }

      // Password is correct and username exists. Clear any previous throttle entries.
      this.loginThrottleService.clearFailures(callerIp);

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
    } catch (error) {
      // If it's already an AuthException (throttle or unauthorised), re-throw
      if (error instanceof HttpException) {
        throw error;
      }

      // Any other error (DB error, etc.) is treated as login failure
      try {
        this.loginThrottleService.recordFailure(callerIp);
      } catch (throttleError) {
        throw throttleError; // Throttle exception takes priority
      }

      throw this.unauthorised();
    }
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
      // Reuse the same family_id to keep the token chain linked
      // If this token is ever stolen and replayed, the entire family is revoked
      const newRefreshTokenIssue = this.refreshTokenService.issueRefreshToken(validation.familyId);

      await this.refreshTokenService.consumeRefreshToken(validation.tokenId);
      await this.refreshTokenService.storeRefreshToken(
        validation.accountId,
        validation.userId,
        validation.roles,
        newRefreshTokenIssue.tokenHash,
        validation.familyId,
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

  async updatePassword(
    currentUser: VerifiedAccessTokenPayload,
    request: ChangePasswordRequest,
  ): Promise<PasswordUpdateResponse> {
    const credential = await this.credentialsRepository.findByAccountId(currentUser.accountId);
    if (!credential) {
      throw this.unauthorised();
    }

    const passwordMatches = await this.passwordService.verify(
      request.currentPassword,
      credential.passwordHash,
    );
    if (!passwordMatches) {
      throw this.unauthorised();
    }

    const nextPasswordHash = await this.passwordService.hash(request.newPassword);
    const updated = await this.credentialsRepository.updatePasswordHash(
      currentUser.accountId,
      nextPasswordHash,
    );
    if (!updated) {
      throw this.unauthorised();
    }

    return { message: 'Password updated successfully.' };
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

  private isForeignKeyViolation(error: unknown): boolean {
    if (!error || typeof error !== 'object') {
      return false;
    }

    const pgError = error as { code?: string };
    return pgError.code === '23503';
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
