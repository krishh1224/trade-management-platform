import { Injectable, LoggerService } from '@nestjs/common';
import { redact } from './redact';

// The one logger this service logs through. Every level redacts sensitive
// key names at any depth before anything reaches the console, so even a
// call site that (against convention) hands over a whole object is covered.
@Injectable()
export class RedactingLogger implements LoggerService {
  log(message: unknown, ...optionalParams: unknown[]): void {
    this.write(console.log, message, optionalParams);
  }

  error(message: unknown, ...optionalParams: unknown[]): void {
    this.write(console.error, message, optionalParams);
  }

  warn(message: unknown, ...optionalParams: unknown[]): void {
    this.write(console.warn, message, optionalParams);
  }

  debug(message: unknown, ...optionalParams: unknown[]): void {
    this.write(console.debug, message, optionalParams);
  }

  verbose(message: unknown, ...optionalParams: unknown[]): void {
    this.write(console.log, message, optionalParams);
  }

  private write(
    sink: (...args: unknown[]) => void,
    message: unknown,
    optionalParams: unknown[],
  ): void {
    const redactedMessage = redact(message);
    const redactedParams = optionalParams.map((param) => redact(param));
    sink(redactedMessage, ...redactedParams);
  }
}
