import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMinSize,
  IsArray,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { IsStrongPassword } from '../../config/validators/is-strong-password.validator';

export const ROLES = ['USER', 'ADMIN'] as const;

export class RegisterRequest {
  @ApiProperty({
    minLength: 3,
    maxLength: 64,
    pattern: '^[a-zA-Z0-9._-]+$',
    example: 'priya.menon',
  })
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/)
  username!: string;

  @ApiProperty({
    minLength: 12,
    maxLength: 128,
    writeOnly: true,
    description:
      'Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character (!@#$%^&*()_+-=[]{}|;:,.<>?).',
    example: 'MySecure!Pass123',
  })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  @IsStrongPassword({
    message:
      'Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character',
  })
  password!: string;

  @ApiProperty({
    type: 'integer',
    format: 'int64',
    minimum: 1,
    description: 'The numeric trading account key, `ACCOUNTS.id`, that this user will trade.',
    example: 101,
  })
  @IsInt()
  @Min(1)
  accountId!: number;

  @ApiPropertyOptional({
    enum: ROLES,
    isArray: true,
    minItems: 1,
    description: 'Optional. Defaults to `["USER"]`.',
    example: ['USER'],
  })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  roles?: string[];
}

export class LoginRequest {
  @ApiProperty({ maxLength: 64, example: 'priya.menon' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(64)
  username!: string;

  @ApiProperty({ maxLength: 128, writeOnly: true, example: 'correct horse battery staple' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  password!: string;
}

export class ChangePasswordRequest {
  @ApiProperty({ maxLength: 128, writeOnly: true, example: 'OldPassword!123' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  currentPassword!: string;

  @ApiProperty({
    minLength: 12,
    maxLength: 128,
    writeOnly: true,
    description:
      'Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character (!@#$%^&*()_+-=[]{}|;:,.<>?).',
    example: 'MyUpdated!Pass123',
  })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  @IsStrongPassword({
    message:
      'Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character',
  })
  newPassword!: string;
}

export class UserResponse {
  @ApiProperty({
    format: 'uuid',
    description: 'The value carried in the `sub` claim.',
    example: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
  })
  id!: string;

  @ApiProperty({ example: 'priya.menon' })
  username!: string;

  @ApiProperty({
    type: 'integer',
    format: 'int64',
    description: 'The numeric trading account key, `ACCOUNTS.id`.',
    example: 101,
  })
  accountId!: number;

  @ApiProperty({ enum: ROLES, isArray: true, minItems: 1, example: ['USER'] })
  roles!: string[];

  @ApiPropertyOptional({ format: 'date-time', example: '2026-10-05T08:00:00Z' })
  createdOn!: string;
}

export class PasswordUpdateResponse {
  @ApiProperty({ example: 'Password updated successfully.' })
  message!: string;
}

export class ErrorResponse {
  @ApiProperty({ enum: ['AUTH-401', 'AUTH-409', 'VAL-422'], example: 'AUTH-401' })
  errorCode!: string;

  @ApiProperty({ example: 'Unauthorised' })
  message!: string;
}
