import { Controller, Get, UseGuards } from '@nestjs/common';
import { VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';
import { CurrentUser } from './current-user.decorator';
import { JwtAuthGuard } from './jwt-auth.guard';

@Controller('auth')
export class AuthController {
  @Get('me')
  @UseGuards(JwtAuthGuard)
  getCurrentUser(
    @CurrentUser() currentUser: VerifiedAccessTokenPayload,
  ): VerifiedAccessTokenPayload {
    return currentUser;
  }
}