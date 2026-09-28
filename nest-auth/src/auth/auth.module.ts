import { Module } from '@nestjs/common';
import { TokenModule } from '../token/token.module';
import { CredentialsModule } from '../credentials/credentials.module';
import { AuthController } from './controllers/auth.controller';
import { AuthService } from './services/auth.service';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginThrottleService } from './services/login-throttle.service';

@Module({
  imports: [TokenModule, CredentialsModule],
  controllers: [AuthController],
  providers: [JwtAuthGuard, AuthService, LoginThrottleService],
})
export class AuthModule {}