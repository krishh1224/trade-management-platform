import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { AccountStatus } from '../api/generated/trade-api/models';
import { InstrumentOption, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { DEFAULT_ACCOUNT_CURRENCY, currencyForSymbol } from '../instruments/instrument-currency';
import { ThemeService } from '../theme/theme.service';
import { FooterComponent } from '../shared/footer.component';
import { NotificationBellComponent } from '../notifications/notification-bell.component';

@Component({
  selector: 'app-orders',
  imports: [ReactiveFormsModule, RouterLink, FooterComponent, NotificationBellComponent],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css'
})
export class OrdersComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly themeService = inject(ThemeService);

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly profileMenuOpen = signal(false);
  readonly theme = this.themeService.theme;
  readonly accountId = computed(() => this.claims()?.accountId ?? null);
  readonly accountStatus = signal<AccountStatus | null>(null);
  readonly accountStatusError = signal('');
  readonly accountCurrency = signal(DEFAULT_ACCOUNT_CURRENCY);
  readonly canPlaceOrder = computed(() => this.accountStatus() === 'ACTIVE');
  readonly orderSubmitting = signal(false);
  readonly orderErrorMessage = signal('');
  readonly orderSuccessMessage = signal('');
  readonly orderStatus = signal<string | null>(null);
  readonly instrumentsLoading = signal(false);
  readonly instrumentsError = signal('');
  readonly instrumentOptions = signal<InstrumentOption[]>([]);
  readonly instrumentQuery = signal('');
  readonly showInstrumentDropdown = signal(false);
  readonly filteredInstrumentOptions = computed(() => {
    const query = this.instrumentQuery().trim().toUpperCase();
    if (query.length === 0) {
      return [];
    }

    return this.instrumentOptions()
      .filter((option) => option.name.toUpperCase().startsWith(query))
      .sort((a, b) => a.name.localeCompare(b.name) || a.symbol.localeCompare(b.symbol));
  });

  readonly orderForm = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(SYMBOL_PATTERN)]],
    side: ['BUY' as 'BUY' | 'SELL', [Validators.required]],
    quantity: ['', [Validators.required, Validators.pattern(POSITIVE_INTEGER_PATTERN)]],
    price: ['', [Validators.required, this.priceValidator]]
  });

  constructor() {
    this.themeService.initialize();
    this.loadAccountStatus();
    this.loadAccountCurrency();
    this.loadInstruments();

    const symbolFromQuery = this.route.snapshot.queryParamMap.get('symbol');
    if (symbolFromQuery) {
      const normalized = symbolFromQuery.trim().toUpperCase();
      this.orderForm.controls.symbol.setValue(normalized);
      this.instrumentQuery.set(normalized);
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

  currentSymbolCurrency(): string {
    const symbol = (this.orderForm.controls.symbol.value ?? '').trim().toUpperCase();
    const listed = this.instrumentOptions().find((option) => option.symbol.toUpperCase() === symbol);
    return listed?.currency ?? currencyForSymbol(symbol);
  }

  /**
   * Shown when the instrument trades in a different currency from the account's cash. The Trade
   * Executor converts at the live FX rate when the order fills, so no amount is quoted here.
   */
  settlementNote(): string {
    if ((this.orderForm.controls.symbol.value ?? '').trim().length === 0) {
      return '';
    }
    const instrumentCurrency = this.currentSymbolCurrency();
    const accountCurrency = this.accountCurrency();
    if (instrumentCurrency === accountCurrency) {
      return '';
    }
    return `${instrumentCurrency} order, settled from your ${accountCurrency} balance at the live ` +
      `${instrumentCurrency}/${accountCurrency} rate at execution.`;
  }

  onInstrumentInput(): void {
    this.instrumentQuery.set(this.orderForm.controls.symbol.value ?? '');
    this.showInstrumentDropdown.set(true);
  }

  onInstrumentFocus(): void {
    if ((this.orderForm.controls.symbol.value ?? '').trim().length > 0) {
      this.showInstrumentDropdown.set(true);
    }
  }

  onInstrumentBlur(): void {
    setTimeout(() => this.showInstrumentDropdown.set(false), 120);
  }

  selectInstrument(option: InstrumentOption): void {
    this.orderForm.controls.symbol.setValue(option.symbol);
    this.instrumentQuery.set(option.symbol);
    this.showInstrumentDropdown.set(false);
  }

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
          this.orderSuccessMessage.set(`Order returned status ${response.status}. ${response.message}`);
        },
        error: (error: unknown) => {
          this.orderErrorMessage.set(this.toReadableOrderError(error));
        }
      });
  }

  private loadInstruments(): void {
    const accountId = this.accountId();
    if (accountId === null) {
      this.instrumentOptions.set([]);
      this.instrumentsError.set('Instruments are unavailable until account details load.');
      return;
    }

    this.instrumentsLoading.set(true);
    this.instrumentsError.set('');
    this.tradeService
      .getInstruments(accountId)
      .pipe(finalize(() => this.instrumentsLoading.set(false)))
      .subscribe({
        next: (options) => {
          const sorted = [...options].sort((a, b) => a.name.localeCompare(b.name) || a.symbol.localeCompare(b.symbol));
          this.instrumentOptions.set(sorted);
        },
        error: () => {
          this.instrumentOptions.set([]);
          this.instrumentsError.set('Unable to load instruments right now.');
        }
      });
  }

  private loadAccountStatus(): void {
    const accountId = this.accountId();
    if (accountId === null) {
      this.accountStatus.set(null);
      this.accountStatusError.set('Unable to determine account status.');
      return;
    }

    this.tradeService
      .getAccount(accountId)
      .pipe(finalize(() => {}))
      .subscribe({
        next: (account) => {
          this.accountStatus.set(account.status);
          if (account.status !== 'ACTIVE') {
            this.accountStatusError.set('Only active accounts can place orders.');
          }
        },
        error: () => {
          this.accountStatus.set(null);
          this.accountStatusError.set('Unable to load account status.');
        }
      });
  }

  private loadAccountCurrency(): void {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }

    // Cash balance currency is the account's base currency; keep the INR default if it can't load.
    this.tradeService.getBalance(accountId).subscribe({
      next: (balance) => {
        if (balance.currency) {
          this.accountCurrency.set(balance.currency.toUpperCase());
        }
      },
      error: () => {}
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

const SYMBOL_PATTERN = /^(?:[A-Z]{1,10}(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z0-9]+(?:-[A-Z0-9]+)*)$/;
const POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;
