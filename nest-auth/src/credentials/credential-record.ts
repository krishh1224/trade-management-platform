export interface CredentialRecord {
  id: number;
  accountId: number;
  username: string;
  role: 'USER' | 'ADMIN';
  passwordHash: string;
  createdOn: Date;
}

