import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { AuthSessionService } from '../auth/auth-session.service';
import { isSafeReturnUrl } from '../auth/return-url';
import { currencyForSymbol } from '../instruments/instrument-currency';
import { FooterComponent } from '../shared/footer.component';
import { ThemeService } from '../theme/theme.service';

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, FooterComponent],
  templateUrl: './sign-in.component.html',
  styleUrl: './sign-in.component.css'
})
export class SignInComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly themeService = inject(ThemeService);

  readonly theme = this.themeService.theme;
  readonly submitting = signal(false);
  readonly errorMessage = signal('');
  readonly showPassword = signal(false);

  /**
   * Sample figures for the landing-page ticker, taken from the seed data: the
   * latest seed order price and its change against the seeded average cost.
   * Not live prices.
   */
  readonly ticker = [
    { symbol: 'INFY', currency: currencyForSymbol('INFY.NS'), price: '1,580.00', change: 8.97 },
    { symbol: 'RELIANCE', currency: currencyForSymbol('RELIANCE.NS'), price: '2,800.00', change: 0 },
    { symbol: 'TATASTEEL', currency: currencyForSymbol('TATASTEEL.BO'), price: '150.00', change: 3.45 },
    { symbol: 'HDFCBANK', currency: currencyForSymbol('HDFCBANK.NS'), price: '1,700.00', change: 3.03 },
    { symbol: 'ICICIBANK', currency: currencyForSymbol('ICICIBANK.NS'), price: '1,000.00', change: 5.26 },
    { symbol: 'ITC', currency: currencyForSymbol('ITC.NS'), price: '450.00', change: 4.65 }
  ];

  readonly signInForm = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  constructor() {
    this.themeService.initialize();

    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

    // Drop an unsafe return address from the address bar. It would be ignored after sign-in anyway.
    if (returnUrl !== null && !isSafeReturnUrl(returnUrl)) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { returnUrl: null },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
    }
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((visible) => !visible);
  }

  scrollTo(sectionId: string): void {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });

    if (sectionId === 'sign-in') {
      document.getElementById('username')?.focus({ preventScroll: true });
    }
  }

  submit(): void {
    if (this.signInForm.invalid) {
      this.signInForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set('');

    this.authSession
      .signIn(this.signInForm.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/');
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.toReadableError(error));
        }
      });
  }

  private toReadableError(error: unknown): string {
    return toUserFacingErrorMessage(error, { fallbackMessage: 'Sign-in failed. Please try again.' });
  }
}