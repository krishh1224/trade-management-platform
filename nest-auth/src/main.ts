import { HttpException, HttpStatus, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { EnvConfig } from './config/env.validation';
import { RedactingLogger } from './logging/redacting-logger.service';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });
  app.useLogger(app.get(RedactingLogger));
  app.useGlobalPipes(
    new ValidationPipe({
      transform: true,
      whitelist: true,
      forbidNonWhitelisted: true,
      exceptionFactory: () =>
        new HttpException(
          { errorCode: 'VAL-422', message: 'Invalid input' },
          HttpStatus.UNPROCESSABLE_ENTITY,
        ),
    }),
  );

  const openApiConfig = new DocumentBuilder()
    .setTitle('Auth service')
    .setVersion('1.0.0')
    .setDescription(
      'Registration, login, token refresh and current-user lookup for the Enterprise Trading ' +
        'Platform. Generated from the running code; the binding contract is contracts/auth-api.yaml.',
    )
    .addTag('Auth', 'Registration, login and token lifecycle.')
    .addTag('Profile', 'The authenticated user.')
    .addBearerAuth({ type: 'http', scheme: 'bearer', bearerFormat: 'JWT' }, 'bearerAuth')
    .build();
  const openApiDocument = SwaggerModule.createDocument(app, openApiConfig);
  SwaggerModule.setup('docs', app, openApiDocument, { jsonDocumentUrl: 'docs-json' });

  const configService = app.get(ConfigService<EnvConfig, true>);
  const port = configService.get('PORT', { infer: true });
  await app.listen(port);
}

bootstrap();
