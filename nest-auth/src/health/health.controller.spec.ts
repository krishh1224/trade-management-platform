import { Test, TestingModule } from '@nestjs/testing';
import { HealthController } from './health.controller';
import { HealthService } from './health.service';

describe('HealthController', () => {
  let controller: HealthController;
  let healthService: { check: jest.Mock };
  let res: { status: jest.Mock; json: jest.Mock };

  beforeEach(async () => {
    healthService = { check: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      controllers: [HealthController],
      providers: [{ provide: HealthService, useValue: healthService }],
    }).compile();

    controller = module.get(HealthController);

    res = { status: jest.fn().mockReturnThis(), json: jest.fn() };
  });

  it('responds 200 when the service reports UP', async () => {
    healthService.check.mockResolvedValue({ status: 'UP', postgresReachable: true });

    await controller.check(res as any);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith({ status: 'UP', postgresReachable: true });
  });

  it('responds 503 when the service reports DOWN', async () => {
    healthService.check.mockResolvedValue({ status: 'DOWN', postgresReachable: false });

    await controller.check(res as any);

    expect(res.status).toHaveBeenCalledWith(503);
    expect(res.json).toHaveBeenCalledWith({ status: 'DOWN', postgresReachable: false });
  });
});
