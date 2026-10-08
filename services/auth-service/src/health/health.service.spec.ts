import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { HealthService } from './health.service';

const queryMock = jest.fn();

jest.mock('pg', () => ({
  Pool: jest.fn().mockImplementation(() => ({
    query: queryMock,
  })),
}));

describe('HealthService', () => {
  let service: HealthService;

  beforeEach(async () => {
    queryMock.mockReset();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        HealthService,
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
      ],
    }).compile();

    service = module.get(HealthService);
  });

  it('reports UP when Postgres answers the ping query', async () => {
    queryMock.mockResolvedValue({ rows: [{ '?column?': 1 }] });

    const result = await service.check();

    expect(result).toEqual({ status: 'UP', postgresReachable: true });
  });

  it('reports DOWN when the ping query fails', async () => {
    queryMock.mockRejectedValue(new Error('connection refused'));

    const result = await service.check();

    expect(result).toEqual({ status: 'DOWN', postgresReachable: false });
  });
});
