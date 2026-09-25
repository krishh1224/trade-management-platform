export interface EnvConfig {
  PORT: number;
  JWT_SECRET: string;
  JWT_ISSUER: string;
  ACCESS_TOKEN_TTL_SECONDS: number;
  REFRESH_TOKEN_TTL_DAYS: number;
  LOGIN_MAX_ATTEMPTS: number;
  LOGIN_LOCKOUT_DURATION_SECONDS: number;
  POSTGRES_HOST: string;
  POSTGRES_PORT: number;
  POSTGRES_DB: string;
  POSTGRES_USER: string;
  POSTGRES_PASSWORD: string;
}

// JWT_SECRET has no default on purpose: this service must refuse to start
// without a real secret, the same convention trade-api's platform profile uses.
const MIN_JWT_SECRET_BYTES = 32;
const CONTRACT_JWT_ISSUER = 'auth-service';
const CONTRACT_ACCESS_TOKEN_TTL_SECONDS = 900;
const CONTRACT_REFRESH_TOKEN_TTL_DAYS = 7;

// Login throttle defaults: 5 failed attempts trigger a 60-second lockout
const DEFAULT_LOGIN_MAX_ATTEMPTS = 5;
const DEFAULT_LOGIN_LOCKOUT_DURATION_SECONDS = 60;

export function validate(env: Record<string, string | undefined>): EnvConfig {
  const jwtSecret = env.JWT_SECRET;
  if (!jwtSecret || Buffer.byteLength(jwtSecret, 'utf8') < MIN_JWT_SECRET_BYTES) {
    throw new Error(
      `JWT_SECRET must be set to a secret of at least ${MIN_JWT_SECRET_BYTES} bytes`,
    );
  }

  const jwtIssuer = env.JWT_ISSUER ?? CONTRACT_JWT_ISSUER;
  if (jwtIssuer !== CONTRACT_JWT_ISSUER) {
    throw new Error(`JWT_ISSUER must be set to ${CONTRACT_JWT_ISSUER}`);
  }

  const accessTokenTtlSeconds = Number(
    env.ACCESS_TOKEN_TTL_SECONDS ?? String(CONTRACT_ACCESS_TOKEN_TTL_SECONDS),
  );
  if (
    !Number.isInteger(accessTokenTtlSeconds) ||
    accessTokenTtlSeconds !== CONTRACT_ACCESS_TOKEN_TTL_SECONDS
  ) {
    throw new Error(
      `ACCESS_TOKEN_TTL_SECONDS must be set to ${CONTRACT_ACCESS_TOKEN_TTL_SECONDS}`,
    );
  }

  const refreshTokenTtlDays = Number(
    env.REFRESH_TOKEN_TTL_DAYS ?? String(CONTRACT_REFRESH_TOKEN_TTL_DAYS),
  );
  if (!Number.isInteger(refreshTokenTtlDays) || refreshTokenTtlDays !== CONTRACT_REFRESH_TOKEN_TTL_DAYS) {
    throw new Error(
      `REFRESH_TOKEN_TTL_DAYS must be set to ${CONTRACT_REFRESH_TOKEN_TTL_DAYS}`,
    );
  }

  const postgresDb = env.POSTGRES_DB;
  if (!postgresDb) {
    throw new Error('POSTGRES_DB must be set');
  }

  const postgresUser = env.POSTGRES_USER;
  if (!postgresUser) {
    throw new Error('POSTGRES_USER must be set');
  }

  const postgresPassword = env.POSTGRES_PASSWORD;
  if (postgresPassword === undefined) {
    throw new Error('POSTGRES_PASSWORD must be set');
  }

  const loginMaxAttempts = Number(env.LOGIN_MAX_ATTEMPTS ?? String(DEFAULT_LOGIN_MAX_ATTEMPTS));
  if (!Number.isInteger(loginMaxAttempts) || loginMaxAttempts < 1) {
    throw new Error('LOGIN_MAX_ATTEMPTS must be a positive integer');
  }

  const loginLockoutDurationSeconds = Number(
    env.LOGIN_LOCKOUT_DURATION_SECONDS ?? String(DEFAULT_LOGIN_LOCKOUT_DURATION_SECONDS),
  );
  if (!Number.isInteger(loginLockoutDurationSeconds) || loginLockoutDurationSeconds < 1) {
    throw new Error('LOGIN_LOCKOUT_DURATION_SECONDS must be a positive integer');
  }

  return {
    PORT: Number(env.PORT ?? 3000),
    JWT_SECRET: jwtSecret,
    JWT_ISSUER: jwtIssuer,
    ACCESS_TOKEN_TTL_SECONDS: accessTokenTtlSeconds,
    REFRESH_TOKEN_TTL_DAYS: refreshTokenTtlDays,
    LOGIN_MAX_ATTEMPTS: loginMaxAttempts,
    LOGIN_LOCKOUT_DURATION_SECONDS: loginLockoutDurationSeconds,
    POSTGRES_HOST: env.POSTGRES_HOST ?? 'localhost',
    POSTGRES_PORT: Number(env.POSTGRES_PORT ?? 5432),
    POSTGRES_DB: postgresDb,
    POSTGRES_USER: postgresUser,
    POSTGRES_PASSWORD: postgresPassword,
  };
}
