import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

export class RefreshRequest {
  @ApiProperty({ description: 'The refresh token issued by the previous login or refresh.' })
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}

export class TokenResponse {
  @ApiProperty({
    description: 'Signed JWT carrying the claims contract described in the service description.',
  })
  accessToken!: string;

  @ApiProperty({ description: 'Opaque, stored server-side, revocable, rotated on every use.' })
  refreshToken!: string;

  @ApiProperty({ enum: ['Bearer'], example: 'Bearer' })
  tokenType: string = 'Bearer';

  @ApiProperty({ type: 'integer', description: 'Access token lifetime in seconds.', example: 900 })
  expiresIn: number = 900; // 15 minutes in seconds
}
