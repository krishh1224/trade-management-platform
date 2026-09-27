import type { Request } from 'express';
import type { VerifiedAccessTokenPayload } from '../../token/services/auth-token.service';

export type AuthenticatedRequest = Request & {
  user?: VerifiedAccessTokenPayload;
};