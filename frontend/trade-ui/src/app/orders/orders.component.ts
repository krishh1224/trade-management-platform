import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

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
    const fallback = 'Order placement failed. Please try again.';

    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (error.status === 0) {
      return 'The service could not be reached. Check whether Trade API is running and CORS allows this UI origin.';
    }

    const errorCode = this.readErrorCode(error.error);

    if (!errorCode) {
      return fallback;
    }

    return ORDER_ERROR_MESSAGES[errorCode] ?? 'An unexpected platform error was returned. Please retry or contact support.';
  }

  private readErrorCode(body: unknown): string | null {
    if (typeof body !== 'object' || body === null || !('errorCode' in body)) {
      return null;
    }

    const errorCode = (body as { errorCode?: unknown }).errorCode;
    return typeof errorCode === 'string' ? errorCode : null;
  }
}

const SYMBOL_PATTERN = /^(?:[A-Z]{1,10}(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z0-9]{2,15})$/;
const POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

const ORDER_ERROR_MESSAGES: Record<string, string> = {
  'ACC-404': 'The account could not be found.',
  'ACC-403': 'This account is not active, or it is not the account this sign-in may trade.',
  'INS-404': 'That instrument is not available for trading.',
  'ORD-400': 'This order could not be placed because there is not enough cash.',
  'ORD-409': 'This order conflicts with your holdings or a duplicate request was received.',
  'VAL-422': 'One or more fields are not acceptable. Check symbol, side, quantity and price.',
  'AUTH-401': 'Your session is not authorised. Sign in again and retry.',
  'AUTH-409': 'That username already exists.'
};
