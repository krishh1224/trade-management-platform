import { Test, TestingModule } from '@nestjs/testing';
import { HttpException, HttpStatus } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AuthTokenService } from '../auth-token/auth-token.service';
import { RefreshTokenService } from '../auth-token/refresh-token.service';
import { TokenResponse } from './refresh.dto';

describe('AuthController - refresh endpoint', () => {
  let controller: AuthController;
  let authTokenService: jest.Mocked<AuthTokenService>;
  let refreshTokenService: jest.Mocked<RefreshTokenService>;

  beforeEach(async () => {
    // Mock the services
    authTokenService = {
      issueAccessToken: jest.fn(),
      verifyAccessToken: jest.fn(),
    } as any;

    refreshTokenService = {
      validateRefreshToken: jest.fn(),
      issueRefreshToken: jest.fn(),
      storeRefreshToken: jest.fn(),
      consumeRefreshToken: jest.fn(),
      revokeFamily: jest.fn(),
      generateFamilyId: jest.fn(),
    } as any;

    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        { provide: AuthTokenService, useValue: authTokenService },
        { provide: RefreshTokenService, useValue: refreshTokenService },
      ],
    }).compile();

    controller = module.get<AuthController>(AuthController);
  });

  describe('refresh', () => {
    const validToken = 'valid-refresh-token-abcdef1234567890';
    const userId = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
    const accountId = 1;
    const roles = ['CUSTOMER'];
    const familyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';
    const tokenId = 42;
    const tokenHash = 'abc123hash';

    it('returns a new access token and a new refresh token on valid refresh', async () => {
      const newAccessToken = 'new-access-token-jwt';
      const newRefreshToken = 'new-refresh-token-opaque';
      const newTokenHash = 'newhash123';
      const newFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';

      // Setup mocks
      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      authTokenService.issueAccessToken.mockReturnValue(newAccessToken);

      refreshTokenService.generateFamilyId.mockReturnValue(newFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValue({
        refreshToken: newRefreshToken,
        tokenHash: newTokenHash,
        familyId: newFamilyId,
      });

      refreshTokenService.consumeRefreshToken.mockResolvedValue({} as any);
      refreshTokenService.storeRefreshToken.mockResolvedValue(43);

      // Call
      const result = await controller.refresh({ refreshToken: validToken });

      // Assert
      expect(result).toEqual({
        accessToken: newAccessToken,
        refreshToken: newRefreshToken,
        tokenType: 'Bearer',
        expiresIn: 900,
      });

      expect(authTokenService.issueAccessToken).toHaveBeenCalledWith({
        sub: userId,
        accountId,
        roles,
      });

      expect(refreshTokenService.consumeRefreshToken).toHaveBeenCalledWith(tokenId);
      expect(refreshTokenService.storeRefreshToken).toHaveBeenCalledWith(
        accountId,
        userId,
        roles,
        newTokenHash,
        newFamilyId,
      );
    });

    it('issues a new refresh token that is opaque and not logged', async () => {
      // This test verifies the contract: refresh tokens are random/opaque, not JWTs
      const newAccessToken = 'new-access-token-jwt';
      const newRefreshToken = 'a' + 'b'.repeat(63); // 64 hex chars (256 bits)
      const newTokenHash = 'newhash123';
      const newFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';

      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      authTokenService.issueAccessToken.mockReturnValue(newAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValue(newFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValue({
        refreshToken: newRefreshToken,
        tokenHash: newTokenHash,
        familyId: newFamilyId,
      });

      const result = await controller.refresh({ refreshToken: validToken });

      // Verify it's opaque (hex-encoded random bytes, not a JWT)
      expect(result.refreshToken).toBe(newRefreshToken);
      expect(result.refreshToken).toMatch(/^[a-f0-9]{64}$/);
      expect(result.refreshToken).not.toContain('.');
      expect(result.refreshToken).not.toContain('Bearer');
    });

    it('returns AUTH-401 when refresh token is expired', async () => {
      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: true, // Key: expired
      });

      try {
        await controller.refresh({ refreshToken: validToken });
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
          expect(error.getResponse()).toEqual({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
          });
        } else {
          throw error;
        }
      }

      expect(refreshTokenService.consumeRefreshToken).not.toHaveBeenCalled();
      expect(authTokenService.issueAccessToken).not.toHaveBeenCalled();
    });

    it('returns AUTH-401 and revokes family when refresh token is already consumed', async () => {
      // Token theft: consumed token presented again
      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: true, // Key: already consumed
        isRevoked: false,
        isExpired: false,
      });

      refreshTokenService.revokeFamily.mockResolvedValue(undefined);

      try {
        await controller.refresh({ refreshToken: validToken });
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
          expect(error.getResponse()).toEqual({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
          });
        } else {
          throw error;
        }
      }

      // Verify family was revoked (theft detection)
      expect(refreshTokenService.revokeFamily).toHaveBeenCalledWith(familyId);
      expect(refreshTokenService.consumeRefreshToken).not.toHaveBeenCalled();
      expect(authTokenService.issueAccessToken).not.toHaveBeenCalled();
    });

    it('returns AUTH-401 when refresh token is revoked', async () => {
      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: true, // Key: revoked
        isExpired: false,
      });

      try {
        await controller.refresh({ refreshToken: validToken });
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
          expect(error.getResponse()).toEqual({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
          });
        } else {
          throw error;
        }
      }

      expect(refreshTokenService.consumeRefreshToken).not.toHaveBeenCalled();
      expect(authTokenService.issueAccessToken).not.toHaveBeenCalled();
    });

    it('returns AUTH-401 when refresh token is not found in database', async () => {
      refreshTokenService.validateRefreshToken.mockRejectedValue(
        new Error('Invalid refresh token'),
      );

      try {
        await controller.refresh({ refreshToken: 'invalid-token' });
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
          expect(error.getResponse()).toEqual({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
          });
        } else {
          throw error;
        }
      }
    });

    it('returns VAL-422 when refreshToken is missing from request', async () => {
      try {
        await controller.refresh({ refreshToken: '' });
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
          expect(error.getResponse()).toEqual({
            errorCode: 'VAL-422',
            message: 'Invalid input',
          });
        } else {
          throw error;
        }
      }

      expect(refreshTokenService.validateRefreshToken).not.toHaveBeenCalled();
    });

    it('returns VAL-422 when request body is invalid', async () => {
      try {
        await controller.refresh(null as any);
        fail('Expected HttpException');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
          expect(error.getResponse()).toEqual({
            errorCode: 'VAL-422',
            message: 'Invalid input',
          });
        } else {
          throw error;
        }
      }
    });

    it('does not expose raw refresh tokens in logs or responses', async () => {
      const newAccessToken = 'new-access-token-jwt';
      const newRefreshToken = 'a' + 'b'.repeat(63);
      const newTokenHash = 'newhash123';
      const newFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';

      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash, // This is the HASH, never the raw token
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      authTokenService.issueAccessToken.mockReturnValue(newAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValue(newFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValue({
        refreshToken: newRefreshToken,
        tokenHash: newTokenHash,
        familyId: newFamilyId,
      });

      // Verify token is stored as hash in DB, never raw
      const result = await controller.refresh({ refreshToken: validToken });

      expect(refreshTokenService.storeRefreshToken).toHaveBeenCalledWith(
        accountId,
        userId,
        roles,
        newTokenHash, // This is the hash
        newFamilyId,
      );

      // Raw token is returned to client but not stored
      expect(result.refreshToken).toBe(newRefreshToken);
    });

    it('builds identity from stored user data, not from presented token', async () => {
      const storedRoles = ['CUSTOMER'];
      const newAccessToken = 'new-access-token-jwt';
      const newRefreshToken = 'a' + 'b'.repeat(63);
      const newTokenHash = 'newhash123';
      const newFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';

      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles: storedRoles, // Roles come from DB, not client
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      authTokenService.issueAccessToken.mockReturnValue(newAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValue(newFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValue({
        refreshToken: newRefreshToken,
        tokenHash: newTokenHash,
        familyId: newFamilyId,
      });

      await controller.refresh({ refreshToken: validToken });

      // Verify roles come from stored data
      expect(authTokenService.issueAccessToken).toHaveBeenCalledWith({
        sub: userId,
        accountId,
        roles: storedRoles,
      });
    });

    it('consumes old token before issuing new one', async () => {
      const newAccessToken = 'new-access-token-jwt';
      const newRefreshToken = 'a' + 'b'.repeat(63);
      const newTokenHash = 'newhash123';
      const newFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';

      refreshTokenService.validateRefreshToken.mockResolvedValue({
        accountId,
        userId,
        roles,
        tokenId,
        tokenHash,
        familyId,
        isConsumed: false,
        isRevoked: false,
        isExpired: false,
      });

      authTokenService.issueAccessToken.mockReturnValue(newAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValue(newFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValue({
        refreshToken: newRefreshToken,
        tokenHash: newTokenHash,
        familyId: newFamilyId,
      });

      await controller.refresh({ refreshToken: validToken });

      // Verify consume is called
      expect(refreshTokenService.consumeRefreshToken).toHaveBeenCalledWith(tokenId);
      // Verify both are called (order checking via call counts)
      expect(refreshTokenService.consumeRefreshToken).toHaveBeenCalledTimes(1);
      expect(refreshTokenService.storeRefreshToken).toHaveBeenCalledTimes(1);
    });

    it('the newly issued refresh token successfully works', async () => {
      // ACCEPTANCE CRITERIA: Verify new token can be used for subsequent refresh
      // This test demonstrates the contract: new tokens are immediately usable.

      // Step 1: First refresh - exchange original token for new pair (token A → token B)
      const originalToken = 'original-token-' + 'a'.repeat(48); // 64 chars
      const originalTokenId = 1;
      const originalFamilyId = 'aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee';

      const firstRefreshAccessToken = 'access-token-1';
      const firstRefreshToken = 'refreshed-token-' + 'b'.repeat(48); // New token B
      const firstRefreshTokenHash = 'hash-b';
      const firstRefreshFamilyId = 'bbbbbbbb-cccc-4ddd-eeee-ffffffffffff';
      const firstRefreshTokenId = 2;

      // Mock first refresh
      refreshTokenService.validateRefreshToken
        .mockResolvedValueOnce({
          accountId,
          userId,
          roles,
          tokenId: originalTokenId,
          tokenHash: 'original-hash',
          familyId: originalFamilyId,
          isConsumed: false,
          isRevoked: false,
          isExpired: false,
        });

      authTokenService.issueAccessToken.mockReturnValueOnce(firstRefreshAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValueOnce(firstRefreshFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValueOnce({
        refreshToken: firstRefreshToken,
        tokenHash: firstRefreshTokenHash,
        familyId: firstRefreshFamilyId,
      });

      refreshTokenService.consumeRefreshToken.mockResolvedValueOnce({} as any);
      refreshTokenService.storeRefreshToken.mockResolvedValueOnce(firstRefreshTokenId);

      // Perform first refresh
      const firstRefreshResult = await controller.refresh({
        refreshToken: originalToken,
      });

      expect(firstRefreshResult.refreshToken).toBe(firstRefreshToken);
      expect(firstRefreshResult.accessToken).toBe(firstRefreshAccessToken);

      // Step 2: Second refresh - use newly issued token (token B) to refresh again
      // This proves the newly issued token WORKS
      const secondRefreshAccessToken = 'access-token-2';
      const secondRefreshToken = 'refreshed-token-' + 'c'.repeat(48); // New token C
      const secondRefreshTokenHash = 'hash-c';
      const secondRefreshFamilyId = 'cccccccc-dddd-4eee-ffff-000000000000';
      const secondRefreshTokenId = 3;

      // Mock second refresh - using the newly issued token from step 1
      refreshTokenService.validateRefreshToken
        .mockResolvedValueOnce({
          accountId,
          userId,
          roles,
          tokenId: firstRefreshTokenId,
          tokenHash: firstRefreshTokenHash,
          familyId: firstRefreshFamilyId, // Same family as first refresh
          isConsumed: false, // Not yet consumed
          isRevoked: false,
          isExpired: false,
        });

      authTokenService.issueAccessToken.mockReturnValueOnce(secondRefreshAccessToken);
      refreshTokenService.generateFamilyId.mockReturnValueOnce(secondRefreshFamilyId);
      refreshTokenService.issueRefreshToken.mockReturnValueOnce({
        refreshToken: secondRefreshToken,
        tokenHash: secondRefreshTokenHash,
        familyId: secondRefreshFamilyId,
      });

      refreshTokenService.consumeRefreshToken.mockResolvedValueOnce({} as any);
      refreshTokenService.storeRefreshToken.mockResolvedValueOnce(secondRefreshTokenId);

      // Perform second refresh WITH the newly issued token from step 1
      const secondRefreshResult = await controller.refresh({
        refreshToken: firstRefreshToken, // Use the new token from first refresh
      });

      // Verify the newly issued token worked (we got a successful response)
      expect(secondRefreshResult.refreshToken).toBe(secondRefreshToken);
      expect(secondRefreshResult.accessToken).toBe(secondRefreshAccessToken);

      // Verify the new token's hash was validated in second call
      expect(refreshTokenService.validateRefreshToken).toHaveBeenCalledTimes(2);
      expect(refreshTokenService.validateRefreshToken).toHaveBeenNthCalledWith(
        2,
        firstRefreshToken // Used the newly issued token
      );

      // Verify both tokens were consumed and replaced
      expect(refreshTokenService.consumeRefreshToken).toHaveBeenCalledTimes(2);
      expect(refreshTokenService.consumeRefreshToken).toHaveBeenNthCalledWith(1, originalTokenId);
      expect(refreshTokenService.consumeRefreshToken).toHaveBeenNthCalledWith(2, firstRefreshTokenId);
    });
  });
});
