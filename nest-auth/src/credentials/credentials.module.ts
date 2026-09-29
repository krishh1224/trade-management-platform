import { Module } from '@nestjs/common';
import { LoggingModule } from '../logging/logging.module';
import { CredentialsRepository } from './credentials.repository';
import { PasswordService } from './password.service';
import { RefreshTokensRepository } from './refresh-tokens.repository';

@Module({
  imports: [LoggingModule],
  providers: [CredentialsRepository, PasswordService, RefreshTokensRepository],
  exports: [CredentialsRepository, PasswordService, RefreshTokensRepository],
})
export class CredentialsModule {}
