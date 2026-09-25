import { Module } from '@nestjs/common';
import { AuthTokenModule } from '../auth-token/auth-token.module';
import { AuthController } from './auth.controller';
import { JwtAuthGuard } from './jwt-auth.guard';

@Module({
  imports: [AuthTokenModule],
  controllers: [AuthController],
  providers: [JwtAuthGuard],
})
export class AuthModule {}