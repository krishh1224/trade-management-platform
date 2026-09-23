import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { CredentialsRepository } from './credentials.repository';
import { RedactingLogger } from '../logging/redacting-logger.service';

const queryMock = jest.fn();

jest.mock('pg', () => ({
  Pool: jest.fn().mockImplementation(() => ({
    query: queryMock,
  })),
}));

describe('CredentialsRepository', () => {
  let repository: CredentialsRepository;
  let logger: { error: jest.Mock };

  beforeEach(async () => {
    queryMock.mockReset();
    logger = { error: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        CredentialsRepository,
        {
          provide: ConfigService,
          useValue: {
            get: (key: string) =>
              ({
                POSTGRES_HOST: 'localhost',
                POSTGRES_PORT: 5432,
                POSTGRES_DB: 'trade_management',
                POSTGRES_USER: 'postgres',
                POSTGRES_PASSWORD: 'postgres',
              })[key],
          },
        },
        { provide: RedactingLogger, useValue: logger },
      ],
    }).compile();

    repository = module.get(CredentialsRepository);
  });

  it('returns the mapped record when a row exists', async () => {
    // pg returns BIGINT columns as strings, not numbers - mocking it that
    // way here is what actually exercises the Number() conversion below.
    queryMock.mockResolvedValue({
      rows: [{ id: '1', account_id: '101', password_hash: '$2b$12$abc' }],
    });

    const result = await repository.findByAccountId(101);

    expect(result).toEqual({
      id: 1,
      accountId: 101,
      passwordHash: '$2b$12$abc',
    });
    expect(result?.id).toBe(1); // not '1'
    expect(result?.accountId).toBe(101); // not '101'
    expect(queryMock).toHaveBeenCalledWith(expect.stringContaining('FROM credentials'), [101]);
  });

  it('returns null when no row matches', async () => {
    queryMock.mockResolvedValue({ rows: [] });

    const result = await repository.findByAccountId(999);

    expect(result).toBeNull();
  });

  it('logs only the account id (never the raw error) and rethrows on query failure', async () => {
    queryMock.mockRejectedValue(new Error('connection refused'));

    await expect(repository.findByAccountId(101)).rejects.toThrow('connection refused');
    expect(logger.error).toHaveBeenCalledWith('credentials lookup failed', {
      accountId: 101,
      errorMessage: 'connection refused',
    });
  });
});
