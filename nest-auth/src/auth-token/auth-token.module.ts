import { Module } from '@nestjs/common';
import { AuthTokenService } from './auth-token.service';
import { RefreshTokenService } from './refresh-token.service';
import { CredentialsModule } from '../credentials/credentials.module';
import { LoggingModule } from '../logging/logging.module';

@Module({
  imports: [CredentialsModule, LoggingModule],
  providers: [AuthTokenService, RefreshTokenService],
  exports: [AuthTokenService, RefreshTokenService],
})
export class AuthTokenModule {}