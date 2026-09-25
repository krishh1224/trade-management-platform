import { RequestMethod } from '@nestjs/common';
import { GUARDS_METADATA, METHOD_METADATA, PATH_METADATA } from '@nestjs/common/constants';
import { VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';
import { AuthController } from './auth.controller';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('AuthController', () => {
  let controller: AuthController;

  beforeEach(() => {
    controller = new AuthController();
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

  it('returns the verified user from the guard without re-decoding in the controller', () => {
    const currentUser: VerifiedAccessTokenPayload = {
      sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      accountId: 1,
      roles: ['CUSTOMER'],
      iat: 1790000000,
      exp: 1790000900,
      iss: 'auth-service',
    };

    expect(controller.getCurrentUser(currentUser)).toEqual(currentUser);
  });
});