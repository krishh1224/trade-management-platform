import { IsNotEmpty, IsString } from 'class-validator';

export class RefreshRequest {
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}

export class TokenResponse {
  accessToken!: string;
  refreshToken!: string;
  tokenType: string = 'Bearer';
  expiresIn: number = 900; // 15 minutes in seconds
}
