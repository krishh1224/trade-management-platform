import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../api/auth.service';
import { ClientProfile, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { FooterComponent } from '../shared/footer.component';
import { ThemeService } from '../theme/theme.service';

@Component({
  selector: 'app-profile',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FooterComponent],
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

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly accountId = computed(() => this.claims()?.accountId ?? null);
  readonly theme = this.themeService.theme;
  readonly profileMenuOpen = signal(false);

  readonly profileLoading = signal(false);
  readonly profileError = signal('');
  readonly profile = signal<ClientProfile | null>(null);

  readonly passwordUpdating = signal(false);
  readonly passwordError = signal('');
  readonly passwordSuccess = signal('');

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
}
