import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';

describe('AuthController - endpoint delegation', () => {
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

  it('delegates register to AuthService', async () => {
    const request = {
      username: 'priya.menon',
      password: 'correct horse battery staple',
      accountId: 101,
    };
    const response = {
      id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
      username: request.username,
      accountId: request.accountId,
      roles: ['CUSTOMER'],
      createdOn: '2026-10-05T08:00:00.000Z',
    };

    authService.register.mockResolvedValue(response);

    await expect(controller.register(request)).resolves.toEqual(response);
    expect(authService.register).toHaveBeenCalledWith(request);
  });

  it('delegates login to AuthService', async () => {
    const request = { username: 'priya.menon', password: 'correct horse battery staple' };
    const response = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    };

    const mockReq = {
      headers: {},
      ip: '192.168.1.1',
      socket: { remoteAddress: '192.168.1.1' },
    } as any;

    authService.login.mockResolvedValue(response);

    await expect(controller.login(request, mockReq)).resolves.toEqual(response);
    expect(authService.login).toHaveBeenCalledWith(request, '192.168.1.1');
  });

  it('delegates refresh to AuthService', async () => {
    const response = {
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    };

    authService.refresh.mockResolvedValue(response);

    await expect(controller.refresh({ refreshToken: 'refresh-token' })).resolves.toEqual(response);
    expect(authService.refresh).toHaveBeenCalledWith('refresh-token');
  });
});
