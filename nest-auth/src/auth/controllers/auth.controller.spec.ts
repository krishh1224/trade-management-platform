import { RequestMethod } from '@nestjs/common';
import { GUARDS_METADATA, METHOD_METADATA, PATH_METADATA } from '@nestjs/common/constants';
import { VerifiedAccessTokenPayload } from '../../token/services/auth-token.service';
import { AuthController } from './auth.controller';
import { UserResponse } from '../dto/auth.dto';
import { AuthService } from '../services/auth.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';

describe('AuthController', () => {
  let controller: AuthController;
  let authService: jest.Mocked<AuthService>;

  beforeEach(() => {
    authService = {
      register: jest.fn(),
      login: jest.fn(),
      refresh: jest.fn(),
      me: jest.fn(),
    } as any;

    controller = new AuthController(authService);
  });

  it('maps to GET /auth/me and is protected by JwtAuthGuard', () => {
    const controllerPath = Reflect.getMetadata(PATH_METADATA, AuthController);
    const methodPath = Reflect.getMetadata(PATH_METADATA, controller.getCurrentUser);
    const requestMethod = Reflect.getMetadata(METHOD_METADATA, controller.getCurrentUser);
    const guards = Reflect.getMetadata(GUARDS_METADATA, controller.getCurrentUser) as Array<
      new (...args: never[]) => unknown
    >;

    expect(controllerPath).toBe('auth');
    expect(methodPath).toBe('me');
    expect(requestMethod).toBe(RequestMethod.GET);
    expect(guards).toContain(JwtAuthGuard);
  });

  it('returns the authenticated user response from AuthService', async () => {
    const currentUser: VerifiedAccessTokenPayload = {
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
      iat: 1790000000,
      exp: 1790000900,
      iss: 'auth-service',
    };

    const response: UserResponse = {
      id: currentUser.sub,
      username: 'priya.menon',
      accountId: 1,
      roles: ['CUSTOMER'],
      createdOn: '2026-10-05T08:00:00.000Z',
    };

    authService.me.mockResolvedValue(response);

    await expect(controller.getCurrentUser(currentUser)).resolves.toEqual(response);
    expect(authService.me).toHaveBeenCalledWith(currentUser);
  });
});