import { Body, Controller, Get, HttpCode, HttpStatus, Patch, Post, Req, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiUnauthorizedResponse,
  ApiUnprocessableEntityResponse,
} from '@nestjs/swagger';
import { Request } from 'express';
import { VerifiedAccessTokenPayload } from '../../token/services/auth-token.service';
import {
  ChangePasswordRequest,
  ErrorResponse,
  LoginRequest,
  PasswordUpdateResponse,
  RegisterRequest,
  UserResponse,
} from '../dto/auth.dto';
import { CurrentUser } from '../decorators/current-user.decorator';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { RefreshRequest, TokenResponse } from '../dto/refresh.dto';
import { AuthService } from '../services/auth.service';
import { LoginThrottleService } from '../services/login-throttle.service';

const ApiInvalidInputResponse = () =>
  ApiUnprocessableEntityResponse({
    description: 'The request failed field validation.',
    type: ErrorResponse,
  });

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('register')
  @ApiTags('Auth')
  @ApiOperation({ operationId: 'register', summary: 'Register a user' })
  @ApiCreatedResponse({ description: 'User created.', type: UserResponse })
  @ApiConflictResponse({ description: 'The username is already taken.', type: ErrorResponse })
  @ApiInvalidInputResponse()
  async register(@Body() request: RegisterRequest): Promise<UserResponse> {
    return this.authService.register(request);
  }

  @Post('login')
  async login(@Body() request: LoginRequest, @Req() req: any): Promise<TokenResponse> {
    const callerIp = req.ip || req.connection?.remoteAddress || 'unknown';
    return this.authService.login(request, callerIp);
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  @ApiTags('Profile')
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ operationId: 'getCurrentUser', summary: 'Get the authenticated user' })
  @ApiOkResponse({ description: 'The authenticated user.', type: UserResponse })
  @ApiUnauthorizedResponse({ description: 'Authentication failed.', type: ErrorResponse })
  async getCurrentUser(
    @CurrentUser() currentUser: VerifiedAccessTokenPayload,
  ): Promise<UserResponse> {
    return this.authService.me(currentUser);
  }

  @Patch('password')
  @UseGuards(JwtAuthGuard)
  @ApiTags('Profile')
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ operationId: 'updatePassword', summary: 'Update password for authenticated user' })
  @ApiOkResponse({ description: 'Password updated.', type: PasswordUpdateResponse })
  @ApiUnauthorizedResponse({ description: 'Authentication failed.', type: ErrorResponse })
  @ApiInvalidInputResponse()
  async updatePassword(
    @CurrentUser() currentUser: VerifiedAccessTokenPayload,
    @Body() request: ChangePasswordRequest,
  ): Promise<PasswordUpdateResponse> {
    return this.authService.updatePassword(currentUser, request);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  @ApiTags('Auth')
  @ApiOperation({
    operationId: 'refresh',
    summary: 'Exchange a refresh token for a new token pair',
  })
  @ApiOkResponse({ description: 'A new token pair.', type: TokenResponse })
  @ApiUnauthorizedResponse({ description: 'Authentication failed.', type: ErrorResponse })
  @ApiInvalidInputResponse()
  async refresh(@Body() request: RefreshRequest): Promise<TokenResponse> {
    return this.authService.refresh(request.refreshToken);
  }
}
