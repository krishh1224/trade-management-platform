import { validate } from './env.validation';

const validEnv = {
  JWT_SECRET: 'a'.repeat(32),
  JWT_ISSUER: 'auth-service',
  ACCESS_TOKEN_TTL_SECONDS: '900',
  POSTGRES_DB: 'trade_management',
  POSTGRES_USER: 'postgres',
  POSTGRES_PASSWORD: 'postgres',
};

describe('validate', () => {
  it('accepts a fully specified environment and applies defaults', () => {
    const config = validate(validEnv);

    expect(config).toEqual({
      PORT: 3000,
      JWT_SECRET: validEnv.JWT_SECRET,
      JWT_ISSUER: 'auth-service',
      ACCESS_TOKEN_TTL_SECONDS: 900,
      REFRESH_TOKEN_TTL_DAYS: 7,
      LOGIN_MAX_ATTEMPTS: 5,
      LOGIN_LOCKOUT_DURATION_SECONDS: 60,
      POSTGRES_HOST: 'localhost',
      POSTGRES_PORT: 5432,
      POSTGRES_DB: 'trade_management',
      POSTGRES_USER: 'postgres',
      POSTGRES_PASSWORD: 'postgres',
    });
  });

  it('honours explicit overrides instead of defaults', () => {
    const config = validate({
      ...validEnv,
      PORT: '4000',
      POSTGRES_HOST: 'postgres',
      POSTGRES_PORT: '5433',
    });

    expect(config.PORT).toBe(4000);
    expect(config.POSTGRES_HOST).toBe('postgres');
    expect(config.POSTGRES_PORT).toBe(5433);
  });

  it('rejects a missing JWT_SECRET', () => {
    const { JWT_SECRET, ...rest } = validEnv;
    expect(() => validate(rest)).toThrow(/JWT_SECRET/);
  });

  it('rejects a JWT_SECRET shorter than 32 bytes', () => {
    expect(() => validate({ ...validEnv, JWT_SECRET: 'too-short' })).toThrow(
      /at least 32 bytes/,
    );
  });

  it('rejects a JWT_ISSUER other than auth-service', () => {
    expect(() => validate({ ...validEnv, JWT_ISSUER: 'some-other-issuer' })).toThrow(/JWT_ISSUER/);
  });

  it('rejects ACCESS_TOKEN_TTL_SECONDS values other than 900', () => {
    expect(() => validate({ ...validEnv, ACCESS_TOKEN_TTL_SECONDS: '600' })).toThrow(
      /ACCESS_TOKEN_TTL_SECONDS/,
    );
  });

  it('rejects a missing POSTGRES_DB', () => {
    const { POSTGRES_DB, ...rest } = validEnv;
    expect(() => validate(rest)).toThrow(/POSTGRES_DB/);
  });

  it('rejects a missing POSTGRES_USER', () => {
    const { POSTGRES_USER, ...rest } = validEnv;
    expect(() => validate(rest)).toThrow(/POSTGRES_USER/);
  });

  it('rejects a missing POSTGRES_PASSWORD', () => {
    const { POSTGRES_PASSWORD, ...rest } = validEnv;
    expect(() => validate(rest)).toThrow(/POSTGRES_PASSWORD/);
  });

  it('accepts an empty-string POSTGRES_PASSWORD', () => {
    expect(() => validate({ ...validEnv, POSTGRES_PASSWORD: '' })).not.toThrow();
  });
});
