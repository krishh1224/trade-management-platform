import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../api/auth.service';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { ProfileComponent } from './profile.component';
import { provideNotificationCountStub } from '../notifications/notification-count.testing';

describe('ProfileComponent', () => {
  const claims = signal({
    sub: 'alice',
    accountId: 7,
    roles: ['USER'],
    iat: 1,
    exp: Math.floor(Date.now() / 1000) + 3600
  });

  const authSessionMock = {
    claims,
    signOut: vi.fn()
  };

  const preferences = { currency: 'INR', theme: 'DARK', alertChannel: 'EMAIL' } as const;

  const tradeServiceMock = {
    getClientProfile: vi.fn().mockReturnValue(
      of({ accountId: 7, name: 'Alice', email: 'alice@trade.test', phone: '99999', dob: '2000-01-01' })
    ),
    getPreferences: vi.fn().mockReturnValue(of(preferences)),
    updatePreferences: vi.fn().mockReturnValue(of(preferences))
  };

  const authServiceMock = {
    updatePassword: vi.fn().mockReturnValue(of({ message: 'Password updated.' }))
  };

  const theme = signal<'dark' | 'light'>('dark');
  const themeServiceMock = {
    theme,
    initialize: vi.fn(),
    setTheme: vi.fn(),
    toggleTheme: vi.fn(() => theme.set(theme() === 'dark' ? 'light' : 'dark'))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    claims.set({
      sub: 'alice',
      accountId: 7,
      roles: ['USER'],
      iat: 1,
      exp: Math.floor(Date.now() / 1000) + 3600
    });
    tradeServiceMock.getClientProfile.mockReturnValue(
      of({ accountId: 7, name: 'Alice', email: 'alice@trade.test', phone: '99999', dob: '2000-01-01' })
    );
    tradeServiceMock.getPreferences.mockReturnValue(of(preferences));

    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideNotificationCountStub(),
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionMock },
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: ThemeService, useValue: themeServiceMock }
      ]
    }).compileComponents();
  });

  it('loads profile on init', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    expect(tradeServiceMock.getClientProfile).toHaveBeenCalledWith(7);
    expect(tradeServiceMock.getPreferences).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.profile()?.name).toBe('Alice');
  });

  it('signing out clears session and navigates to sign-in', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.signOut();

    expect(authSessionMock.signOut).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith('/sign-in');
  });

  it('marks fields when password form is invalid', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const markSpy = vi.spyOn(fixture.componentInstance.passwordForm, 'markAllAsTouched');

    fixture.componentInstance.updatePassword();

    expect(markSpy).toHaveBeenCalled();
    expect(authServiceMock.updatePassword).not.toHaveBeenCalled();
  });

  it('updates password and shows success on valid form', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.componentInstance.passwordForm.patchValue({
      currentPassword: 'OldPassword!23',
      newPassword: 'NewPassword!23'
    });

    fixture.componentInstance.updatePassword();

    expect(authServiceMock.updatePassword).toHaveBeenCalledWith('OldPassword!23', 'NewPassword!23');
    expect(fixture.componentInstance.passwordSuccess()).toContain('Password updated');
  });

  it('shows error when password update fails', () => {
    authServiceMock.updatePassword.mockReturnValueOnce(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.componentInstance.passwordForm.patchValue({
      currentPassword: 'OldPassword!23',
      newPassword: 'NewPassword!23'
    });

    fixture.componentInstance.updatePassword();

    expect(fixture.componentInstance.passwordError()).toContain('Unable to update password');
  });

  it('shows profile error when there is no account id in claims', () => {
    claims.set({
      sub: 'alice',
      accountId: null as unknown as number,
      roles: ['USER'],
      iat: 1,
      exp: Math.floor(Date.now() / 1000) + 3600
    });

    const fixture = TestBed.createComponent(ProfileComponent);

    expect(fixture.componentInstance.profileError()).toContain('Unable to load account profile');
    expect(tradeServiceMock.getClientProfile).not.toHaveBeenCalled();
  });

  it('toggles theme and profile menu state from component actions', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;

    expect(component.theme()).toBe('dark');
    component.toggleTheme();
    expect(component.theme()).toBe('light');

    expect(component.profileMenuOpen()).toBe(false);
    component.toggleProfileMenu();
    expect(component.profileMenuOpen()).toBe(true);
    component.closeProfileMenu();
    expect(component.profileMenuOpen()).toBe(false);
  });

  it('shows profile load error when profile API request fails', () => {
    tradeServiceMock.getClientProfile.mockReturnValueOnce(throwError(() => new Error('down')));

    const fixture = TestBed.createComponent(ProfileComponent);

    expect(fixture.componentInstance.profile()).toBeNull();
    expect(fixture.componentInstance.profileError()).toContain('Unable to load profile details right now');
  });
});