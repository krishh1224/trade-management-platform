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
      rows: [{ id: '1', account_id: '101', username: 'priya.menon', roles: 'USER', password_hash: '$2b$12$abc', created_at: '2026-10-05T08:00:00.000Z' }],
    });

    const result = await repository.findByAccountId(101);

    expect(result).toEqual({
      id: 1,
      accountId: 101,
      username: 'priya.menon',
      role: 'USER',
      passwordHash: '$2b$12$abc',
      createdOn: new Date('2026-10-05T08:00:00.000Z'),
    });
    expect(result?.id).toBe(1); // not '1'
    expect(result?.accountId).toBe(101); // not '101'
    expect(queryMock).toHaveBeenCalledWith(expect.stringContaining('FROM auth.credentials'), [101]);
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

  it('finds a credential by username and returns the mapped record', async () => {
    queryMock.mockResolvedValue({
      rows: [{ id: '2', account_id: '42', username: 'aditya.kumar', roles: 'ADMIN', password_hash: '$2b$12$xyz', created_at: '2026-10-06T08:00:00.000Z' }],
    });

    const result = await repository.findByUsername('aditya.kumar');

    expect(result).toEqual({
      id: 2,
      accountId: 42,
      username: 'aditya.kumar',
      role: 'ADMIN',
      passwordHash: '$2b$12$xyz',
      createdOn: new Date('2026-10-06T08:00:00.000Z'),
    });
    expect(queryMock).toHaveBeenCalledWith(expect.stringContaining('FROM auth.credentials WHERE username'), [
      'aditya.kumar',
    ]);
  });

  it('returns null when no credential matches the username', async () => {
    queryMock.mockResolvedValue({ rows: [] });

    const result = await repository.findByUsername('nonexistent');

    expect(result).toBeNull();
  });

  it('logs only the username (never the raw error) and rethrows on findByUsername query failure', async () => {
    queryMock.mockRejectedValue(new Error('connection refused'));

    await expect(repository.findByUsername('aditya.kumar')).rejects.toThrow('connection refused');
    expect(logger.error).toHaveBeenCalledWith('credentials lookup by username failed', {
      username: 'aditya.kumar',
      errorMessage: 'connection refused',
    });
  });

  it('creates a credential row and returns mapped record', async () => {
    queryMock.mockResolvedValue({
      rows: [
        {
          id: '3',
          account_id: '77',
          username: 'new.user',
          roles: 'USER',
          password_hash: '$2b$12$abcxyz',
          created_at: '2026-10-07T08:00:00.000Z',
        },
      ],
    });

    const result = await repository.createCredential(77, 'new.user', '$2b$12$abcxyz', 'USER');

    expect(result).toEqual({
      id: 3,
      accountId: 77,
      username: 'new.user',
      role: 'USER',
      passwordHash: '$2b$12$abcxyz',
      createdOn: new Date('2026-10-07T08:00:00.000Z'),
    });
    expect(queryMock).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO auth.credentials'),
      [77, 'new.user', 'USER', '$2b$12$abcxyz'],
    );
  });
});
