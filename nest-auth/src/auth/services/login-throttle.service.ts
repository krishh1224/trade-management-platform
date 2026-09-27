import { Injectable, HttpException, HttpStatus } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EnvConfig } from '../../config/env.validation';

export interface ThrottleEntry {
  failedAttempts: number;
  lastFailureTime: number;
  lockedUntil: number | null;
}

/**
 * Enforces login throttling to mitigate brute-force attacks.
 *
 * Throttle behavior:
 * - After MAX_FAILED_ATTEMPTS (5) failures, the caller is locked out
 * - Lockout duration is LOCKOUT_DURATION_SECONDS (60 seconds)
 * - After lockout expires, failure count resets
 * - Each failed attempt restarts the lockout timer
 *
 * Caller identification: IP address from request (X-Forwarded-For or remoteAddress)
 */
@Injectable()
export class LoginThrottleService {
  private readonly throttleMap = new Map<string, ThrottleEntry>();
  private readonly maxFailedAttempts: number;
  private readonly lockoutDurationSeconds: number;

  constructor(private readonly configService: ConfigService<EnvConfig, true>) {
    this.maxFailedAttempts = this.configService.get('LOGIN_MAX_ATTEMPTS');
    this.lockoutDurationSeconds = this.configService.get('LOGIN_LOCKOUT_DURATION_SECONDS');
  }

  /**
   * Check if the caller is currently throttled.
   * @param callerIp IP address of the caller
   * @throws HttpException with AUTH-401 if throttled
   */
  checkThrottle(callerIp: string): void {
    const entry = this.throttleMap.get(callerIp);
    if (!entry) {
      return; // Not throttled
    }

    const now = Date.now();

    // If lockout period has expired, reset
    if (entry.lockedUntil && now > entry.lockedUntil) {
      this.throttleMap.delete(callerIp);
      return;
    }

    // If currently locked out, reject
    if (entry.lockedUntil && now <= entry.lockedUntil) {
      throw this.throttleException();
    }
  }

  /**
   * Record a failed login attempt.
   * @param callerIp IP address of the caller
   * @throws HttpException with AUTH-401 if max attempts exceeded
   */
  recordFailure(callerIp: string): void {
    const now = Date.now();
    let entry = this.throttleMap.get(callerIp);

    if (!entry) {
      // First failure
      entry = {
        failedAttempts: 1,
        lastFailureTime: now,
        lockedUntil: null,
      };
      this.throttleMap.set(callerIp, entry);
      return;
    }

    // If previous lockout has expired, reset counter
    if (entry.lockedUntil && now > entry.lockedUntil) {
      entry.failedAttempts = 1;
      entry.lastFailureTime = now;
      entry.lockedUntil = null;
      return;
    }

    // Increment failure count
    entry.failedAttempts += 1;
    entry.lastFailureTime = now;

    // Lock out if max attempts reached
    if (entry.failedAttempts >= this.maxFailedAttempts) {
      entry.lockedUntil = now + this.lockoutDurationSeconds * 1000;
      throw this.throttleException();
    }
  }

  /**
   * Clear throttle entry for successful login (optional, for symmetry).
   * @param callerIp IP address of the caller
   */
  clearFailures(callerIp: string): void {
    this.throttleMap.delete(callerIp);
  }

  /**
   * Extract caller IP from request headers.
   * Respects X-Forwarded-For for proxy scenarios, falls back to remoteAddress.
   */
  static extractCallerIp(headers: any, remoteAddress: string): string {
    const xForwardedFor = headers['x-forwarded-for'];
    if (xForwardedFor) {
      // X-Forwarded-For can be a comma-separated list; take the first (original client)
      const firstIp = (xForwardedFor as string).split(',')[0].trim();
      return firstIp;
    }
    return remoteAddress || 'unknown';
  }

  private throttleException(): HttpException {
    return new HttpException(
      { errorCode: 'AUTH-401', message: 'Unauthorised' },
      HttpStatus.UNAUTHORIZED,
    );
  }
}
