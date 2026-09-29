import { RedactingLogger } from './redacting-logger.service';

describe('RedactingLogger', () => {
  let logger: RedactingLogger;
  let consoleLogSpy: jest.SpyInstance;
  let consoleErrorSpy: jest.SpyInstance;

  beforeEach(() => {
    logger = new RedactingLogger();
    consoleLogSpy = jest.spyOn(console, 'log').mockImplementation();
    consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();
  });

  afterEach(() => {
    consoleLogSpy.mockRestore();
    consoleErrorSpy.mockRestore();
  });

  it('redacts sensitive keys in the primary log message', () => {
    logger.log({ email: 'a@b.com', password: 'hunter2' });

    expect(consoleLogSpy).toHaveBeenCalledWith({ email: 'a@b.com', password: '[REDACTED]' });
  });

  it('redacts sensitive keys in additional log arguments too', () => {
    logger.error('login failed', { attemptedPassword: 'hunter2' });

    expect(consoleErrorSpy).toHaveBeenCalledWith('login failed', {
      attemptedPassword: '[REDACTED]',
    });
  });

  it('passes plain string messages through unchanged', () => {
    logger.log('service started');

    expect(consoleLogSpy).toHaveBeenCalledWith('service started');
  });
});
