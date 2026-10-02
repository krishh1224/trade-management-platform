import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';

@Component({
  selector: 'app-orders',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css'
})
export class OrdersComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly accountId = computed(() => this.claims()?.accountId ?? null);
  readonly orderSubmitting = signal(false);
  readonly orderErrorMessage = signal('');
  readonly orderSuccessMessage = signal('');
  readonly orderStatus = signal<string | null>(null);

  readonly orderForm = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(SYMBOL_PATTERN)]],
    side: ['BUY' as 'BUY' | 'SELL', [Validators.required]],
    quantity: ['', [Validators.required, Validators.pattern(POSITIVE_INTEGER_PATTERN)]],
    price: ['', [Validators.required, this.priceValidator]]
  });

  constructor() {}

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  submitOrder(): void {
    if (this.orderForm.invalid) {
      this.orderForm.markAllAsTouched();
      return;
    }

    const accountId = this.accountId();
    if (accountId === null) {
      this.orderErrorMessage.set('The signed-in account could not be read from this session. Please sign in again.');
      this.orderSuccessMessage.set('');
      this.orderStatus.set(null);
      return;
    }

    const { symbol, side, quantity, price } = this.orderForm.getRawValue();

    this.orderSubmitting.set(true);
    this.orderErrorMessage.set('');
    this.orderSuccessMessage.set('');
    this.orderStatus.set(null);

    this.tradeService
      .placeOrder({
        accountId,
        symbol: symbol.trim().toUpperCase(),
        side,
        quantity: Number(quantity),
        price: Number(price),
        idempotencyKey: crypto.randomUUID()
      })
      .pipe(finalize(() => this.orderSubmitting.set(false)))
      .subscribe({
        next: (response) => {
          this.orderStatus.set(response.status);
          this.orderSuccessMessage.set(`Order ${response.orderId} returned status ${response.status}. ${response.message}`);
        },
        error: (error: unknown) => {
          this.orderErrorMessage.set(this.toReadableOrderError(error));
        }
      });
  }

  private priceValidator(control: AbstractControl<string>): ValidationErrors | null {
    const value = control.value?.trim() ?? '';

    if (value.length === 0) {
      return null;
    }

    if (!PRICE_PATTERN.test(value)) {
      return { priceFormat: true };
    }

    if (Number(value) <= 0) {
      return { pricePositive: true };
    }

    return null;
  }

  private toReadableOrderError(error: unknown): string {
    return toUserFacingErrorMessage(error, { fallbackMessage: 'Order placement failed. Please try again.' });
  }
}

const SYMBOL_PATTERN = /^(?:[A-Z]{1,10}(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z0-9]{2,15})$/;
const POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;
