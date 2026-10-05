import { AppModule } from './app.module';
import { AuthModule } from './auth/auth.module';
import { TokenModule } from './token/token.module';
import { CredentialsModule } from './credentials/credentials.module';
import { HealthModule } from './health/health.module';
import { LoggingModule } from './logging/logging.module';
import { LoginRequest, RegisterRequest, ROLES, UserResponse } from './auth/auth.dto';

describe('module wiring and DTO exports', () => {
  it('exposes module classes and auth DTO constants', () => {
    expect(AppModule).toBeDefined();
    expect(AuthModule).toBeDefined();
    expect(TokenModule).toBeDefined();
    expect(CredentialsModule).toBeDefined();
    expect(HealthModule).toBeDefined();
    expect(LoggingModule).toBeDefined();

    expect(ROLES).toEqual(['USER', 'ADMIN']);

    const register = new RegisterRequest();
    register.username = 'priya.menon';
    register.password = 'MySecure!Pass123';
    register.accountId = 101;
    register.roles = ['USER'];

    const login = new LoginRequest();
    login.username = 'priya.menon';
    login.password = 'MySecure!Pass123';

    const user = new UserResponse();
    user.id = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
    user.username = 'priya.menon';
    user.accountId = 101;
    user.roles = ['USER'];
    user.createdOn = '2026-10-05T08:00:00Z';

    expect(register.username).toBe('priya.menon');
    expect(login.username).toBe('priya.menon');
    expect(user.roles).toContain('USER');
  });
});
