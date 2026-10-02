import {
  AfterViewInit,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { CashMovementType, CashTransactionResponse } from '../api/generated/trade-api/models';
import { FundsStore, describeFundsError, errorCodeOf } from './funds.store';

const AMOUNT_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;

// quick add buttons on the deposit tab
const QUICK_AMOUNTS = [10000, 50000, 100000];

// checks here are only for the user, the api validates everything again
@Component({
  selector: 'app-funds-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './funds-dialog.component.html',
  styleUrl: './funds-dialog.component.css'
})
export class FundsDialogComponent implements AfterViewInit {
  readonly store = inject(FundsStore);
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly quickAmounts = QUICK_AMOUNTS;
  readonly mode = signal<CashMovementType>('DEPOSIT');
  readonly confirming = signal(false);
  readonly submitting = signal(false);
  readonly result = signal<CashTransactionResponse | null>(null);
  readonly errorMessage = signal('');

  // reused when retrying the same transfer
  private idempotencyKey: string | null = null;

  readonly amount = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, amountFormat, (control) => this.withinAvailable(control)]
  });

  private readonly amountValue = toSignal(this.amount.valueChanges, { initialValue: '' });
  private readonly amountStatus = toSignal(this.amount.statusChanges, { initialValue: this.amount.status });

  readonly parsedAmount = computed(() => {
    this.amountStatus();
    return this.amount.valid ? Number(this.amountValue()) : null;
  });

  readonly submitLabel = computed(() => {
    const verb = this.mode() === 'DEPOSIT' ? 'Deposit' : 'Withdraw';
    const amount = this.parsedAmount();
    if (this.submitting()) {
      return this.mode() === 'DEPOSIT' ? 'Depositing…' : 'Withdrawing…';
    }
    return amount === null ? verb : `${verb} ${this.money(amount)}`;
  });

  constructor() {
    this.store.load();

    // recheck the amount when the balance loads or the tab changes
    effect(() => {
      this.mode();
      this.store.available();
      untracked(() => this.amount.updateValueAndValidity());
    });
  }

  // new amount means a new transfer, so reset the old state
  onAmountInput(): void {
    this.resetAttempt();
  }

  ngAfterViewInit(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  selectMode(mode: CashMovementType): void {
    if (this.mode() === mode) {
      return;
    }
    this.mode.set(mode);
    this.amount.reset('');
    this.resetAttempt();
  }

  addQuickAmount(step: number): void {
    const current = this.amount.valid ? Number(this.amount.value) : 0;
    this.setAmount(current + step);
  }

  useMaximum(): void {
    this.setAmount(this.store.available());
  }

  // no formGroup on this form, so handle the native submit ourselves
  onSubmit(event: Event): void {
    event.preventDefault();
    this.submit();
  }

  submit(): void {
    if (this.amount.invalid || this.submitting()) {
      this.amount.markAsTouched();
      return;
    }

    // ask for confirmation before a withdrawal
    if (this.mode() === 'WITHDRAWAL' && !this.confirming()) {
      this.confirming.set(true);
      return;
    }

    const amount = Number(this.amount.value);
    this.idempotencyKey ??= newIdempotencyKey();
    this.submitting.set(true);
    this.errorMessage.set('');

    this.store
      .move(this.mode(), amount, this.idempotencyKey)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (transaction) => {
          this.result.set(transaction);
          this.confirming.set(false);
          this.amount.reset('');
          this.idempotencyKey = null;
        },
        error: (error: unknown) => {
          this.errorMessage.set(describeFundsError(error));
          this.confirming.set(false);
          // key already used or balance changed, refresh and use a new key next time
          if (errorCodeOf(error) === 'CSH-409') {
            this.idempotencyKey = null;
            this.store.load();
          }
        }
      });
  }

  cancelConfirm(): void {
    this.confirming.set(false);
  }

  close(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.close === 'function' && dialog.open) {
      dialog.close();
    }
    this.closed.emit();
  }

  // esc key
  onCancel(event: Event): void {
    event.preventDefault();
    this.close();
  }

  // clicked outside the panel
  onBackdropClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      this.close();
    }
  }

  money(value: number): string {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: this.store.currency(),
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  }

  when(iso: string): string {
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(iso));
  }

  private setAmount(value: number): void {
    this.amount.setValue(value > 0 ? value.toFixed(2) : '');
    this.amount.markAsTouched();
    this.resetAttempt();
  }

  private resetAttempt(): void {
    this.confirming.set(false);
    this.errorMessage.set('');
    this.result.set(null);
    this.idempotencyKey = null;
  }

  private withinAvailable(control: AbstractControl<string>): ValidationErrors | null {
    if (this.mode() !== 'WITHDRAWAL' || !AMOUNT_PATTERN.test(control.value)) {
      return null;
    }
    return Number(control.value) > this.store.available() ? { exceedsAvailable: true } : null;
  }
}

function amountFormat(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value;
  if (!value) {
    return null;
  }
  if (!AMOUNT_PATTERN.test(value)) {
    return { format: true };
  }
  return Number(value) > 0 ? null : { format: true };
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `funds-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
