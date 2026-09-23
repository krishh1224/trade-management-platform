export interface EnvConfig {
  PORT: number;
  JWT_SECRET: string;
  POSTGRES_HOST: string;
  POSTGRES_PORT: number;
  POSTGRES_DB: string;
  POSTGRES_USER: string;
  POSTGRES_PASSWORD: string;
}

// JWT_SECRET has no default on purpose: this service must refuse to start
// without a real secret, the same convention trade-api's platform profile uses.
const MIN_JWT_SECRET_BYTES = 32;

export function validate(env: Record<string, string | undefined>): EnvConfig {
  const jwtSecret = env.JWT_SECRET;
  if (!jwtSecret || Buffer.byteLength(jwtSecret, 'utf8') < MIN_JWT_SECRET_BYTES) {
    throw new Error(
      `JWT_SECRET must be set to a secret of at least ${MIN_JWT_SECRET_BYTES} bytes`,
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

  return {
    PORT: Number(env.PORT ?? 3000),
    JWT_SECRET: jwtSecret,
    POSTGRES_HOST: env.POSTGRES_HOST ?? 'localhost',
    POSTGRES_PORT: Number(env.POSTGRES_PORT ?? 5432),
    POSTGRES_DB: postgresDb,
    POSTGRES_USER: postgresUser,
    POSTGRES_PASSWORD: postgresPassword,
  };
}
