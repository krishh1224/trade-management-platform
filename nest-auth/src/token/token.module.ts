import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthTokenService } from './services/auth-token.service';
import { RefreshTokenService } from './services/refresh-token.service';
import { CredentialsModule } from '../credentials/credentials.module';
import { LoggingModule } from '../logging/logging.module';

@Module({
  imports: [ConfigModule, CredentialsModule, LoggingModule],
  providers: [AuthTokenService, RefreshTokenService],
  exports: [AuthTokenService, RefreshTokenService],
})
export class TokenModule {}