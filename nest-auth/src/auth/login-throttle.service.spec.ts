import { HttpException, HttpStatus } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { LoginThrottleService } from './login-throttle.service';

describe('LoginThrottleService', () => {
  let service: LoginThrottleService;
  let configService: jest.Mocked<ConfigService>;

  beforeEach(() => {
    configService = {
      get: jest.fn(),
    } as any;

    // Default config: 5 max attempts, 60 second lockout
    configService.get.mockImplementation((key: string) => {
      if (key === 'LOGIN_MAX_ATTEMPTS') return 5;
      if (key === 'LOGIN_LOCKOUT_DURATION_SECONDS') return 60;
      return undefined;
    });

    service = new LoginThrottleService(configService);
  });

  describe('checkThrottle', () => {
    it('should allow access when no throttle entry exists', () => {
      expect(() => service.checkThrottle('192.168.1.1')).not.toThrow();
    });

    it('should allow access when lockout period has expired', () => {
      // Manually create an expired entry
      const pastTime = Date.now() - 61000; // 61 seconds ago
      const entry = {
        failedAttempts: 5,
        lastFailureTime: pastTime,
        lockedUntil: pastTime + 1000, // Locked for 1 second, now expired
      };
      (service as any).throttleMap.set('192.168.1.1', entry);

      expect(() => service.checkThrottle('192.168.1.1')).not.toThrow();
      // Entry should be cleared
      expect((service as any).throttleMap.has('192.168.1.1')).toBe(false);
    });

    it('should throw AUTH-401 when currently locked out', () => {
      // Manually create a current lockout
      const now = Date.now();
      const entry = {
        failedAttempts: 5,
        lastFailureTime: now - 1000,
        lockedUntil: now + 30000, // Locked for another 30 seconds
      };
      (service as any).throttleMap.set('192.168.1.1', entry);

      expect(() => service.checkThrottle('192.168.1.1')).toThrow(HttpException);
      try {
        service.checkThrottle('192.168.1.1');
      } catch (error) {
        if (error instanceof HttpException) {
          expect(error.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
          expect(error.getResponse()).toEqual({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
          });
        }
      }
    });
  });

  describe('recordFailure', () => {
    it('should create a new throttle entry on first failure', () => {
      service.recordFailure('192.168.1.1');

      const entry = (service as any).throttleMap.get('192.168.1.1');
      expect(entry).toBeDefined();
      expect(entry.failedAttempts).toBe(1);
      expect(entry.lockedUntil).toBeNull();
    });

    it('should increment failure count up to max attempts', () => {
      const ip = '192.168.1.1';

      // First 4 attempts: should not throw
      for (let i = 1; i < 5; i++) {
        service.recordFailure(ip);
        const entry = (service as any).throttleMap.get(ip);
        expect(entry.failedAttempts).toBe(i);
      }
    });

    it('should throw AUTH-401 and lock out on max attempts reached', () => {
      const ip = '192.168.1.1';

      // Fail 5 times (max attempts)
      for (let i = 0; i < 4; i++) {
        service.recordFailure(ip);
      }

      // 5th attempt should throw
      expect(() => service.recordFailure(ip)).toThrow(HttpException);

      const entry = (service as any).throttleMap.get(ip);
      expect(entry.failedAttempts).toBe(5);
      expect(entry.lockedUntil).toBeDefined();
      expect(entry.lockedUntil).toBeGreaterThan(Date.now());
    });

    it('should reset counter after lockout expires', () => {
      const ip = '192.168.1.1';

      // Reach max attempts
      for (let i = 0; i < 5; i++) {
        try {
          service.recordFailure(ip);
        } catch {
          // Expected on 5th attempt
        }
      }

      // Manually expire the lockout
      const entry = (service as any).throttleMap.get(ip);
      entry.lockedUntil = Date.now() - 1000; // Now in the past

      // Next failure should reset the counter
      service.recordFailure(ip);
      const newEntry = (service as any).throttleMap.get(ip);
      expect(newEntry.failedAttempts).toBe(1);
      expect(newEntry.lockedUntil).toBeNull();
    });
  });

  describe('clearFailures', () => {
    it('should remove throttle entry', () => {
      const ip = '192.168.1.1';
      service.recordFailure(ip);

      expect((service as any).throttleMap.has(ip)).toBe(true);

      service.clearFailures(ip);

      expect((service as any).throttleMap.has(ip)).toBe(false);
    });
  });

  describe('extractCallerIp', () => {
    it('should extract IP from X-Forwarded-For header (first IP)', () => {
      const headers = { 'x-forwarded-for': '203.0.113.1, 198.51.100.1, 192.0.2.1' };
      const ip = LoginThrottleService.extractCallerIp(headers, '127.0.0.1');
      expect(ip).toBe('203.0.113.1');
    });

    it('should handle X-Forwarded-For with single IP', () => {
      const headers = { 'x-forwarded-for': '203.0.113.1' };
      const ip = LoginThrottleService.extractCallerIp(headers, '127.0.0.1');
      expect(ip).toBe('203.0.113.1');
    });

    it('should fall back to remoteAddress when X-Forwarded-For missing', () => {
      const headers = {};
      const ip = LoginThrottleService.extractCallerIp(headers, '127.0.0.1');
      expect(ip).toBe('127.0.0.1');
    });

    it('should return "unknown" if no IP information available', () => {
      const headers = {};
      const ip = LoginThrottleService.extractCallerIp(headers, '');
      expect(ip).toBe('unknown');
    });
  });
});
