import { Module } from '@nestjs/common';
import { AuthTokenModule } from '../auth-token/auth-token.module';
import { CredentialsModule } from '../credentials/credentials.module';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';
import { LoginThrottleService } from './login-throttle.service';

@Module({
  imports: [AuthTokenModule, CredentialsModule],
  controllers: [AuthController],
  providers: [JwtAuthGuard, AuthService, LoginThrottleService],
})
export class AuthModule {}