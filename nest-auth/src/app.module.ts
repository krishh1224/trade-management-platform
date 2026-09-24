import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthTokenModule } from './auth-token/auth-token.module';
import { validate } from './config/env.validation';
import { CredentialsModule } from './credentials/credentials.module';
import { HealthModule } from './health/health.module';
import { LoggingModule } from './logging/logging.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validate,
    }),
    LoggingModule,
    HealthModule,
    CredentialsModule,
    AuthTokenModule,
  ],
})
export class AppModule {}
