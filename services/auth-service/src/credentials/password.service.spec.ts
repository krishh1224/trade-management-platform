import { PasswordService } from './password.service';

describe('PasswordService', () => {
  const service = new PasswordService();

  it('verifies a correct password', async () => {
    const hash = await service.hash('correct-horse-battery-staple');

    await expect(service.verify('correct-horse-battery-staple', hash)).resolves.toBe(
      true,
    );
  });

  it('fails verification for an incorrect password', async () => {
    const hash = await service.hash('correct-horse-battery-staple');

    await expect(service.verify('wrong-password', hash)).resolves.toBe(false);
  });

  it('does not use a general-purpose digest such as MD5 or SHA', async () => {
    const hash = await service.hash('correct-horse-battery-staple');

    // bcrypt's PHC-style prefix ($2a$/$2b$/$2y$ + cost + salt). A bare MD5
    // (32 hex chars) or SHA-256 (64 hex chars) digest would never match this
    // shape, and would also never encode a cost factor into the string.
    expect(hash).toMatch(/^\$2[aby]\$\d{2}\$/);
    expect(hash).not.toMatch(/^[0-9a-f]{32}$/i);
    expect(hash).not.toMatch(/^[0-9a-f]{64}$/i);
  });

  it('encodes the configured cost factor (>= 12) into the hash', async () => {
    const hash = await service.hash('correct-horse-battery-staple');
    const [, , costPart] = hash.split('$');

    expect(Number(costPart)).toBeGreaterThanOrEqual(12);
  });
});
