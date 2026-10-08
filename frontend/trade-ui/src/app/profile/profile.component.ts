import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../api/auth.service';
import {
  ClientProfile,
  CustomerPreferences,
  PreferenceAlertChannel,
  PreferenceTheme,
  TradeService
} from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { FooterComponent } from '../shared/footer.component';
import { AppTheme, ThemeService } from '../theme/theme.service';
import { BrowserPushService } from '../notifications/browser-push.service';
import { NotificationBellComponent } from '../notifications/notification-bell.component';

@Component({
  selector: 'app-profile',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FooterComponent, NotificationBellComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly authService = inject(AuthService);
  private readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly browserPush = inject(BrowserPushService);

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly accountId = computed(() => this.claims()?.accountId ?? null);
  readonly theme = this.themeService.theme;
  readonly profileMenuOpen = signal(false);

  readonly profileLoading = signal(false);
  readonly profileError = signal('');
  readonly profile = signal<ClientProfile | null>(null);

  readonly preferencesLoading = signal(false);
  readonly preferencesSaving = signal(false);
  readonly preferencesError = signal('');
  readonly preferencesSuccess = signal('');

  readonly pushState = this.browserPush.state;
  readonly pushBusy = this.browserPush.busy;
  readonly pushError = this.browserPush.error;

  readonly passwordUpdating = signal(false);
  readonly passwordError = signal('');
  readonly passwordSuccess = signal('');

  readonly preferenceOptions = {
    currencies: ['INR', 'USD'] as const,
    themes: ['LIGHT', 'DARK'] as const,
    alertChannels: ['EMAIL', 'PUSH', 'BROWSER', 'NONE'] as const
  };

  readonly preferencesForm = this.formBuilder.nonNullable.group({
    currency: this.formBuilder.nonNullable.control<'INR' | 'USD'>('INR', [Validators.required]),
    theme: this.formBuilder.nonNullable.control<PreferenceTheme>('DARK', [Validators.required]),
    alertChannel: this.formBuilder.nonNullable.control<PreferenceAlertChannel>('NONE', [Validators.required])
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).+$/)
      ]
    ]
  });

  constructor() {
    this.themeService.initialize();
    this.loadProfile();
    this.loadPreferences();
    void this.browserPush.refresh();
  }

  enablePush(): void {
    const accountId = this.accountId();
    if (accountId !== null) {
      void this.browserPush.enable(accountId);
    }
  }

  disablePush(): void {
    const accountId = this.accountId();
    if (accountId !== null) {
      void this.browserPush.disable(accountId);
    }
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  closeProfileMenu(): void {
    this.profileMenuOpen.set(false);
  }

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  updatePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    this.passwordUpdating.set(true);
    this.passwordError.set('');
    this.passwordSuccess.set('');

    this.authService
      .updatePassword(currentPassword, newPassword)
      .pipe(finalize(() => this.passwordUpdating.set(false)))
      .subscribe({
        next: (response) => {
          this.passwordSuccess.set(response.message);
          this.passwordForm.reset();
        },
        error: () => {
          this.passwordError.set('Unable to update password. Check your current password and try again.');
        }
      });
  }

  savePreferences(): void {
    const id = this.accountId();
    if (id === null || this.preferencesForm.invalid) {
      this.preferencesForm.markAllAsTouched();
      return;
    }

    this.preferencesSaving.set(true);
    this.preferencesError.set('');
    this.preferencesSuccess.set('');

    const body: CustomerPreferences = this.preferencesForm.getRawValue();

    this.tradeService
      .updatePreferences(id, body)
      .pipe(finalize(() => this.preferencesSaving.set(false)))
      .subscribe({
        next: (preferences) => {
          this.applyPreferencesToForm(preferences);
          this.themeService.setTheme(this.toAppTheme(preferences.theme));
          this.preferencesSuccess.set('Preferences saved.');
        },
        error: () => {
          this.preferencesError.set('Unable to save preferences right now.');
        }
      });
  }

  private loadProfile(): void {
    const id = this.accountId();
    if (id === null) {
      this.profileError.set('Unable to load account profile for this session.');
      return;
    }

    this.profileLoading.set(true);
    this.profileError.set('');

    this.tradeService
      .getClientProfile(id)
      .pipe(finalize(() => this.profileLoading.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
        },
        error: () => {
          this.profile.set(null);
          this.profileError.set('Unable to load profile details right now.');
        }
      });
  }

  private loadPreferences(): void {
    const id = this.accountId();
    if (id === null) {
      return;
    }

    this.preferencesLoading.set(true);
    this.preferencesError.set('');

    this.tradeService
      .getPreferences(id)
      .pipe(finalize(() => this.preferencesLoading.set(false)))
      .subscribe({
        next: (preferences) => {
          this.applyPreferencesToForm(preferences);
          this.themeService.setTheme(this.toAppTheme(preferences.theme));
        },
        error: () => {
          this.preferencesError.set('Unable to load preferences right now.');
        }
      });
  }

  private applyPreferencesToForm(preferences: CustomerPreferences): void {
    this.preferencesForm.patchValue({
      ...preferences,
      alertChannel: preferences.alertChannel === 'SMS' ? 'NONE' : preferences.alertChannel
    });
  }

  private toAppTheme(theme: PreferenceTheme): AppTheme {
    return theme === 'DARK' ? 'dark' : 'light';
  }
}
