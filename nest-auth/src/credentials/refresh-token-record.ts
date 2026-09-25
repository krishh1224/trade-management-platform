export interface RefreshTokenRecord {
  id: number;
  accountId: number;
  userId: string; // UUID
  roles: string[]; // Stored at token issuance time
  tokenHash: string;
  familyId: string; // UUID
  expiresAt: Date;
  consumedAt: Date | null;
  revokedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}
