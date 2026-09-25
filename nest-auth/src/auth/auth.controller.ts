import { Body, Controller, Get, HttpCode, HttpStatus, Post, UseGuards, Req } from '@nestjs/common';
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
import { VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';
import { ErrorResponse, LoginRequest, RegisterRequest, UserResponse } from './auth.dto';
import { CurrentUser } from './current-user.decorator';
import { JwtAuthGuard } from './jwt-auth.guard';
import { RefreshRequest, TokenResponse } from './refresh.dto';
import { AuthService } from './auth.service';
import { LoginThrottleService } from './login-throttle.service';

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
  async login(@Body() request: LoginRequest): Promise<TokenResponse> {
    return this.authService.login(request);
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
