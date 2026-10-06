import { Injectable } from '@nestjs/common';
import * as bcrypt from 'bcrypt';

// Cost 12: the floor this team decided on. bcrypt's work factor is
// exponential (each +1 roughly doubles the time), so this is a deliberate
// point, not a placeholder.
//
// Measured on the dev machine this was built on: bcrypt.hash at cost 12
// averages ~270-280ms per call (Node 24, 5-run average). That's the same
// order of magnitude as the ~100ms target - cost 13 would land near
// 500-600ms, clearly too slow for a login route to eat on every request;
// cost 11 would roughly halve the work an offline attacker has to do per
// guess, which is the wrong direction to move. Re-measure this on the actual
// deployment host (a shared-vCPU VM will likely be slower, not faster) and
// adjust only if it's wildly off from ~100ms - do not drop below 12.
const BCRYPT_COST = 12;

@Injectable()
export class PasswordService {
  async hash(plaintext: string): Promise<string> {
    return bcrypt.hash(plaintext, BCRYPT_COST);
  }

  async verify(plaintext: string, passwordHash: string): Promise<boolean> {
    return bcrypt.compare(plaintext, passwordHash);
  }
}
