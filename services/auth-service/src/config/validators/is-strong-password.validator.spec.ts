import { IsStrongPassword, IsStrongPasswordConstraint } from './is-strong-password.validator';

describe('IsStrongPasswordConstraint', () => {
  const constraint = new IsStrongPasswordConstraint();

  it('rejects empty values', () => {
    expect(constraint.validate('')).toBe(false);
  });

  it('rejects passwords shorter than minimum length', () => {
    expect(constraint.validate('Ab1!short')).toBe(false);
  });

  it('rejects passwords without uppercase', () => {
    expect(constraint.validate('lowercase123!')).toBe(false);
  });

  it('rejects passwords without lowercase', () => {
    expect(constraint.validate('UPPERCASE123!')).toBe(false);
  });

  it('rejects passwords without digits', () => {
    expect(constraint.validate('NoDigitsHere!')).toBe(false);
  });

  it('rejects passwords without special characters', () => {
    expect(constraint.validate('NoSpecialChars123')).toBe(false);
  });

  it('accepts strong passwords', () => {
    expect(constraint.validate('MySecure!Pass123')).toBe(true);
  });

  it('returns the expected default error message', () => {
    expect(constraint.defaultMessage()).toContain('at least 12 characters');
  });
});

describe('IsStrongPassword decorator', () => {
  it('applies without throwing', () => {
    class TestDto {
      @IsStrongPassword()
      password!: string;
    }

    const instance = new TestDto();
    instance.password = 'MySecure!Pass123';

    expect(instance.password).toBe('MySecure!Pass123');
    expect(instance).toBeInstanceOf(TestDto);
  });
});
