import { Module } from '@nestjs/common';
import { LoggingModule } from '../logging/logging.module';
import { CredentialsRepository } from './credentials.repository';
import { PasswordService } from './password.service';

@Module({
  imports: [LoggingModule],
  providers: [CredentialsRepository, PasswordService],
  exports: [CredentialsRepository, PasswordService],
})
export class CredentialsModule {}
