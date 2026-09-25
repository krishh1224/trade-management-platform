import type { Request } from 'express';
import type { VerifiedAccessTokenPayload } from '../auth-token/auth-token.service';

export type AuthenticatedRequest = Request & {
  user?: VerifiedAccessTokenPayload;
};