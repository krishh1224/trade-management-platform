import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module';
import { TokenModule } from './token/token.module';
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
    AuthModule,
    CredentialsModule,
    TokenModule,
  ],
})
export class AppModule {}
