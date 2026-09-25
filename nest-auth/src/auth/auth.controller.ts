import { Body, Controller, Get, Post, UseGuards, Req } from '@nestjs/common';
import { Request } from 'express';
import { VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';
import { LoginRequest, RegisterRequest, UserResponse } from './auth.dto';
import { CurrentUser } from './current-user.decorator';
import { JwtAuthGuard } from './jwt-auth.guard';
import { RefreshRequest, TokenResponse } from './refresh.dto';
import { AuthService } from './auth.service';
import { LoginThrottleService } from './login-throttle.service';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('register')
  async register(@Body() request: RegisterRequest): Promise<UserResponse> {
    return this.authService.register(request);
  }

  @Post('login')
  async login(@Body() request: LoginRequest, @Req() req: Request): Promise<TokenResponse> {
    const callerIp = LoginThrottleService.extractCallerIp(
      req.headers,
      req.ip || req.socket.remoteAddress || 'unknown',
    );
    return this.authService.login(request, callerIp);
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  async getCurrentUser(
    @CurrentUser() currentUser: VerifiedAccessTokenPayload,
  ): Promise<UserResponse> {
    return this.authService.me(currentUser);
  }

  @Post('refresh')
  async refresh(@Body() request: RefreshRequest): Promise<TokenResponse> {
    return this.authService.refresh(request.refreshToken);
  }
}