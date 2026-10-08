import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
} from '@nestjs/common';
import { AuthTokenService } from '../../token/services/auth-token.service';
import { AuthenticatedRequest } from '../interfaces/authenticated-request';

const AUTH_UNAUTHORISED_RESPONSE = {
  errorCode: 'AUTH-401',
  message: 'Unauthorised',
} as const;

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly authTokenService: AuthTokenService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const token = this.extractBearerToken(request.headers.authorization);
    if (!token) {
      throw this.unauthorised();
    }

    try {
      const verifiedPayload = this.authTokenService.verifyAccessToken(token);
      request.user = verifiedPayload;
      return true;
    } catch {
      throw this.unauthorised();
    }
  }

  private extractBearerToken(authorizationHeader: string | string[] | undefined): string | null {
    if (typeof authorizationHeader !== 'string') {
      return null;
    }

    const [scheme, token, ...rest] = authorizationHeader.trim().split(/\s+/);
    if (rest.length > 0 || !scheme || !token) {
      return null;
    }

    if (scheme.toLowerCase() !== 'bearer') {
      return null;
    }

    return token;
  }

  private unauthorised(): HttpException {
    return new HttpException(AUTH_UNAUTHORISED_RESPONSE, HttpStatus.UNAUTHORIZED);
  }
}