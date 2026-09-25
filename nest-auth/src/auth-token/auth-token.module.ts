import { Module } from '@nestjs/common';
import { AuthTokenService } from './auth-token.service';
import { RefreshTokenService } from './refresh-token.service';
import { CredentialsModule } from '../credentials/credentials.module';

@Module({
  imports: [CredentialsModule],
  providers: [AuthTokenService, RefreshTokenService],
  exports: [AuthTokenService, RefreshTokenService],
})
export class AuthTokenModule {}