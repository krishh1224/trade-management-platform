import { NestFactory } from '@nestjs/core';
import { SwaggerModule } from '@nestjs/swagger';

jest.mock('@nestjs/core', () => ({
  NestFactory: {
    create: jest.fn(),
  },
}));

class MockDocumentBuilder {
  setTitle(): this {
    return this;
  }

  setVersion(): this {
    return this;
  }

  setDescription(): this {
    return this;
  }

  addTag(): this {
    return this;
  }

  addBearerAuth(): this {
    return this;
  }

  build(): Record<string, unknown> {
    return { openapi: '3.0.0' };
  }
}

jest.mock('@nestjs/swagger', () => {
  const actual = jest.requireActual('@nestjs/swagger');

  class LocalDocumentBuilder {
    setTitle(): this {
      return this;
    }

    setVersion(): this {
      return this;
    }

    setDescription(): this {
      return this;
    }

    addTag(): this {
      return this;
    }

    addBearerAuth(): this {
      return this;
    }

    build(): Record<string, unknown> {
      return { openapi: '3.0.0' };
    }
  }

  return {
    ...actual,
    DocumentBuilder: LocalDocumentBuilder,
    SwaggerModule: {
      createDocument: jest.fn(() => ({ paths: {} })),
      setup: jest.fn(),
    },
  };
});

describe('main bootstrap', () => {
  it('boots the app, configures middleware and listens on configured port', async () => {
    const getMock = jest.fn(() => ({ get: jest.fn(() => 3100) }));
    const app = {
      useLogger: jest.fn(),
      useGlobalPipes: jest.fn(),
      enableCors: jest.fn(),
      get: getMock,
      listen: jest.fn().mockResolvedValue(undefined),
    };

    (NestFactory.create as jest.Mock).mockResolvedValue(app);

    await import('./main');
    await new Promise((resolve) => setImmediate(resolve));

    expect(NestFactory.create).toHaveBeenCalledTimes(1);
    expect(app.useLogger).toHaveBeenCalledTimes(1);
    expect(app.useGlobalPipes).toHaveBeenCalledTimes(1);
    expect(SwaggerModule.createDocument).toHaveBeenCalledTimes(1);
    expect(SwaggerModule.setup).toHaveBeenCalledWith(
      'docs',
      app,
      expect.any(Object),
      expect.objectContaining({ jsonDocumentUrl: 'docs-json' }),
    );
    expect(app.listen).toHaveBeenCalledWith(3100);
  });
});
