import { BadRequestException, Controller, Get, HttpException, HttpStatus, Post, UseGuards, Body } from '@nestjs/common';
import { VerifiedAccessTokenPayload, AuthTokenService } from '../auth-token/auth-token.service';
import { RefreshTokenService } from '../auth-token/refresh-token.service';
import { CurrentUser } from './current-user.decorator';
import { JwtAuthGuard } from './jwt-auth.guard';
import { RefreshRequest, TokenResponse } from './refresh.dto';

@Controller('auth')
export class AuthController {
  constructor(
    private readonly authTokenService: AuthTokenService,
    private readonly refreshTokenService: RefreshTokenService,
  ) {}

  @Get('me')
  @UseGuards(JwtAuthGuard)
  getCurrentUser(
    @CurrentUser() currentUser: VerifiedAccessTokenPayload,
  ): VerifiedAccessTokenPayload {
    return currentUser;
  }

  @Post('refresh')
  async refresh(@Body() request: RefreshRequest): Promise<TokenResponse> {
    // Validate input
    if (!request || typeof request.refreshToken !== 'string' || request.refreshToken.length === 0) {
      throw new HttpException(
        { errorCode: 'VAL-422', message: 'Invalid input' },
        HttpStatus.UNPROCESSABLE_ENTITY,
      );
    }

    try {
      // Validate the presented refresh token
      const validation = await this.refreshTokenService.validateRefreshToken(request.refreshToken);

      // Check if token is expired, consumed, or revoked
      if (validation.isExpired) {
        throw new HttpException(
          { errorCode: 'AUTH-401', message: 'Unauthorised' },
          HttpStatus.UNAUTHORIZED,
        );
      }

      if (validation.isRevoked) {
        throw new HttpException(
          { errorCode: 'AUTH-401', message: 'Unauthorised' },
          HttpStatus.UNAUTHORIZED,
        );
      }

      if (validation.isConsumed) {
        // Token theft detected: a consumed token is being presented again
        // Revoke the entire family
        await this.refreshTokenService.revokeFamily(validation.familyId);
        throw new HttpException(
          { errorCode: 'AUTH-401', message: 'Unauthorised' },
          HttpStatus.UNAUTHORIZED,
        );
      }

      // Token is valid. Now consume it and issue new pair.
      // Build identity for new token from stored data (not from JWT/client)
      const subject = {
        sub: validation.userId,
        accountId: validation.accountId,
        roles: [...validation.roles], // From stored token record, not client-supplied
      };

      // Issue new tokens
      const newAccessToken = this.authTokenService.issueAccessToken(subject);
      const newFamilyId = this.refreshTokenService.generateFamilyId();
      const newRefreshTokenIssue = this.refreshTokenService.issueRefreshToken(newFamilyId);

      // Store new refresh token and consume old one
      // TODO: Make this a database transaction when login is implemented
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
      // If it's already an HttpException, re-throw it
      if (error instanceof HttpException) {
        throw error;
      }

      // Any other error (token not found, DB error, etc.) is treated as invalid token
      throw new HttpException(
        { errorCode: 'AUTH-401', message: 'Unauthorised' },
        HttpStatus.UNAUTHORIZED,
      );
    }
  }
}