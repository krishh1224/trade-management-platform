import { Module } from '@nestjs/common';
import { RedactingLogger } from './redacting-logger.service';

@Module({
  providers: [RedactingLogger],
  exports: [RedactingLogger],
})
export class LoggingModule {}
