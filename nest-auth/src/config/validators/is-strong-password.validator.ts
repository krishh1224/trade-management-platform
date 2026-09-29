import {
  registerDecorator,
  ValidationOptions,
  ValidatorConstraint,
  ValidatorConstraintInterface,
} from 'class-validator';

/**
 * Password Policy Validator
 *
 * Enforces strict password requirements:
 * - Minimum 12 characters
 * - At least one uppercase letter (A-Z)
 * - At least one lowercase letter (a-z)
 * - At least one digit (0-9)
 * - At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)
 */
@ValidatorConstraint({ name: 'isStrongPassword', async: false })
export class IsStrongPasswordConstraint implements ValidatorConstraintInterface {
  private readonly MIN_LENGTH = 12;
  private readonly SPECIAL_CHARS = /[!@#$%^&*()_+=\[\]{}|;:,.<>?-]/;
  private readonly UPPERCASE_PATTERN = /[A-Z]/;
  private readonly LOWERCASE_PATTERN = /[a-z]/;
  private readonly DIGIT_PATTERN = /[0-9]/;

  validate(password: string): boolean {
    if (!password) {
      return false;
    }

    // Check minimum length
    if (password.length < this.MIN_LENGTH) {
      return false;
    }

    // Check uppercase
    if (!this.UPPERCASE_PATTERN.test(password)) {
      return false;
    }

    // Check lowercase
    if (!this.LOWERCASE_PATTERN.test(password)) {
      return false;
    }

    // Check digit
    if (!this.DIGIT_PATTERN.test(password)) {
      return false;
    }

    // Check special character
    if (!this.SPECIAL_CHARS.test(password)) {
      return false;
    }

    return true;
  }

  defaultMessage(): string {
    return `Password must be at least ${this.MIN_LENGTH} characters and contain uppercase, lowercase, number, and special character`;
  }
}

/**
 * Decorator to apply strong password validation
 *
 * @example
 * class RegisterRequest {
 *   @IsStrongPassword()
 *   password: string;
 * }
 */
export function IsStrongPassword(options?: ValidationOptions) {
  return function (target: object, propertyName: string) {
    registerDecorator({
      target: target.constructor,
      propertyName: propertyName,
      options: options,
      constraints: [],
      validator: IsStrongPasswordConstraint,
    });
  };
}
