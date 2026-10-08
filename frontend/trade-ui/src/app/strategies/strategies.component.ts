import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { readErrorCode, toUserFacingErrorMessage } from '../api/error-catalogue';
import {
  CreateStrategyRequest,
  ExecutionOutcome,
  Strategy,
  StrategyChart,
  StrategyExecution,
  StrategyService,
  StrategyType
} from '../api/strategy.service';
import { InstrumentOption, TradeService } from '../api/trade.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { currencyForSymbol, currencySymbolForDisplay } from '../instruments/instrument-currency';
import { NotificationBellComponent } from '../notifications/notification-bell.component';
import { ThemeService } from '../theme/theme.service';
import { StrategyChartComponent } from './strategy-chart.component';

/** Statuses and firings are re-read this often, so a strategy acting (or being halted) shows up promptly. */
const REFRESH_INTERVAL_MS = 10_000;

const OUTCOME_LABELS: Readonly<Record<ExecutionOutcome, string>> = {
  PLACED: 'Order placed',
  FAILED: 'Failed',
  SKIPPED_DISABLED: 'Not placed: disabled',
  SKIPPED_SPEND_LIMIT: 'Not placed: spend limit',
  SKIPPED_POSITION_LIMIT: 'Not placed: position limit',
  SKIPPED_NO_HOLDING: 'Not placed: nothing to sell'
};

/**
 * Automated strategies: configure a rule once and the platform places its orders from the live price
 * stream. Each strategy can be switched off at once (Disable takes effect before the call returns), and
 * its activity shows every time its rule fired and what was done, including what the limits stopped.
 */
@Component({
  selector: 'app-strategies',
  imports: [CommonModule, RouterLink, ReactiveFormsModule, NotificationBellComponent, StrategyChartComponent],
  templateUrl: './strategies.component.html',
  // the shared page shell lives with the Watchlist page; form controls and badges with the Alerts page
  styleUrls: ['../watchlist/watchlist.component.css', '../alerts/alerts.component.css', './strategies.component.css']
})
export class StrategiesComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly strategyService = inject(StrategyService);
  private readonly tradeService = inject(TradeService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly fb = inject(FormBuilder);
  private readonly now = signal(Date.now());

  readonly claims = this.authSession.claims;
  readonly navOpen = signal(false);
  readonly profileMenuOpen = signal(false);
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;

  readonly strategies = signal<Strategy[]>([]);
  readonly maxStrategies = signal(0);
  readonly strategiesLoading = signal(false);
  readonly strategiesError = signal('');
  /** Strategy ids with a disable, enable or delete in flight. */
  readonly busy = signal<ReadonlySet<number>>(new Set());

  readonly instruments = signal<InstrumentOption[]>([]);
  readonly instrumentsError = signal('');

  readonly selectedId = signal<number | null>(null);
  readonly executions = signal<StrategyExecution[]>([]);
  readonly executionsLoading = signal(false);
  readonly executionsError = signal('');

  readonly chart = signal<StrategyChart | null>(null);
  readonly chartLoading = signal(false);
  readonly chartError = signal('');

  readonly strategyForm: FormGroup;
  readonly formType = signal<StrategyType>('MA_CROSSOVER');
  readonly formSubmitting = signal(false);
  readonly formError = signal('');
  readonly formSuccess = signal('');

  readonly atLimit = computed(() => this.maxStrategies() > 0 && this.strategies().length >= this.maxStrategies());
  readonly selected = computed(() => this.strategies().find((s) => s.strategyId === this.selectedId()) ?? null);
  readonly enabledCount = computed(() => this.strategies().filter((s) => s.status === 'ENABLED').length);

  readonly secondsLeft = computed(() => {
    const exp = this.claims()?.exp;
    return exp === undefined ? null : Math.max(0, Math.floor(exp - this.now() / 1000));
  });

  readonly sessionExpired = computed(() => this.secondsLeft() === 0);

  constructor() {
    this.themeService.initialize();
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    const refresh = setInterval(() => {
      this.loadStrategies(true);
      this.loadExecutions(true);
    }, REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      clearInterval(refresh);
    });

    this.strategyForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(60)]],
      symbol: [null as string | null, Validators.required],
      type: ['MA_CROSSOVER' as StrategyType, Validators.required],
      shortWindow: [5, [Validators.min(2), Validators.max(199)]],
      longWindow: [20, [Validators.min(3), Validators.max(200)]],
      bandWindow: [20, [Validators.min(2), Validators.max(200)]],
      bandWidth: [2, [Validators.min(0.001), Validators.max(5)]],
      quantity: [1, [Validators.required, Validators.min(1), Validators.max(100000)]],
      maxSpend: [null as number | null, [Validators.required, Validators.min(0.01)]],
      maxPosition: [null as number | null, [Validators.required, Validators.min(1)]],
      maxConsecutiveFailures: [3, [Validators.required, Validators.min(1), Validators.max(10)]]
    });
    this.strategyForm.get('type')!.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((type: StrategyType) => this.formType.set(type));

    this.loadStrategies();
    this.loadInstruments();
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

  currencySymbol(symbol: string): string {
    const currency = this.instruments().find((i) => i.symbol === symbol)?.currency;
    return currencySymbolForDisplay(currency ?? currencyForSymbol(symbol));
  }

  ruleSummary(strategy: Strategy): string {
    return strategy.type === 'MA_CROSSOVER'
      ? `MA crossover ${strategy.shortWindow} / ${strategy.longWindow}`
      : `Bollinger ${strategy.bandWindow}, ${strategy.bandWidth} sd`;
  }

  outcomeLabel(outcome: ExecutionOutcome): string {
    return OUTCOME_LABELS[outcome];
  }

  isBusy(strategyId: number): boolean {
    return this.busy().has(strategyId);
  }

  select(strategy: Strategy): void {
    this.selectedId.set(strategy.strategyId);
    this.executions.set([]);
    this.chart.set(null);
    this.loadExecutions();
  }

  submit(): void {
    if (!this.claims()) {
      return;
    }
    this.formError.set('');
    const problems = this.formProblems();
    if (problems.length > 0) {
      this.strategyForm.markAllAsTouched();
      this.formError.set(problems.join(' '));
      return;
    }
    const value = this.strategyForm.value;
    const body: CreateStrategyRequest = {
      name: String(value.name).trim(),
      symbol: value.symbol,
      type: value.type,
      quantity: Number(value.quantity),
      maxSpend: Number(value.maxSpend),
      maxPosition: Number(value.maxPosition),
      maxConsecutiveFailures: Number(value.maxConsecutiveFailures)
    };
    if (value.type === 'MA_CROSSOVER') {
      body.shortWindow = Number(value.shortWindow);
      body.longWindow = Number(value.longWindow);
    } else {
      body.bandWindow = Number(value.bandWindow);
      body.bandWidth = Number(value.bandWidth);
    }
    if (body.maxPosition < body.quantity) {
      this.formError.set('The maximum position must be at least the order quantity, or no buy could ever be placed.');
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set('');
    this.formSuccess.set('');
    this.strategyService.create(this.claims()!.accountId, body)
      .pipe(finalize(() => this.formSubmitting.set(false)))
      .subscribe({
        next: (created) => {
          this.formSuccess.set(`${created.name} is on: it will trade ${created.symbol} when its rule fires.`);
          setTimeout(() => this.formSuccess.set(''), 5000);
          this.strategyForm.patchValue({ name: '' });
          this.strategyForm.get('name')?.markAsUntouched();
          this.loadStrategies();
          this.select(created);
        },
        error: (error) => {
          // the server says which rule failed (e.g. "Strategy limit reached"); show it rather than a generic line
          const message = readErrorCode(error) === 'VAL-422' ? (error?.error?.message as string | undefined) : undefined;
          this.formError.set(
            message && message !== 'Invalid input'
              ? message
              : toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to create the strategy. Please try again.' })
          );
        }
      });
  }

  /** What is wrong with the form, field by field, in the words the API's limits are stated in. */
  formProblems(): string[] {
    const value = this.strategyForm.value;
    const invalid = (name: string) => this.strategyForm.get(name)?.invalid ?? false;
    const problems: string[] = [];
    if (invalid('name')) {
      problems.push('Enter a name (up to 60 characters).');
    }
    if (invalid('symbol')) {
      problems.push('Choose an instrument.');
    }
    if (value.type === 'MA_CROSSOVER') {
      if (invalid('shortWindow')) {
        problems.push('Short window must be between 2 and 199.');
      }
      if (invalid('longWindow')) {
        problems.push('Long window must be between 3 and 200.');
      }
      if (!invalid('shortWindow') && !invalid('longWindow') && Number(value.shortWindow) >= Number(value.longWindow)) {
        problems.push('Short window must be smaller than long window.');
      }
    } else {
      if (invalid('bandWindow')) {
        problems.push('Band window must be between 2 and 200.');
      }
      if (invalid('bandWidth')) {
        problems.push('Band width must be more than 0 and at most 5.');
      }
    }
    if (invalid('quantity')) {
      problems.push('Shares per order must be between 1 and 100000.');
    }
    if (invalid('maxSpend')) {
      problems.push('Enter the most it may spend (more than 0).');
    }
    if (invalid('maxPosition')) {
      problems.push('Enter the most shares to hold (at least 1).');
    }
    if (invalid('maxConsecutiveFailures')) {
      problems.push('Failed orders before stopping must be between 1 and 10.');
    }
    return problems;
  }

  disable(strategy: Strategy): void {
    this.act(strategy, this.strategyService.disable(this.claims()!.accountId, strategy.strategyId), 'disable');
  }

  enable(strategy: Strategy): void {
    this.act(strategy, this.strategyService.enable(this.claims()!.accountId, strategy.strategyId), 'enable');
  }

  delete(strategy: Strategy): void {
    if (!this.claims()) {
      return;
    }
    this.setBusy(strategy.strategyId, true);
    this.strategiesError.set('');
    this.strategyService.delete(this.claims()!.accountId, strategy.strategyId)
      .pipe(finalize(() => this.setBusy(strategy.strategyId, false)))
      .subscribe({
        next: () => {
          this.strategies.update((list) => list.filter((s) => s.strategyId !== strategy.strategyId));
          if (this.selectedId() === strategy.strategyId) {
            this.selectedId.set(null);
            this.executions.set([]);
            this.chart.set(null);
          }
        },
        error: (error) => this.strategiesError.set(
          toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to delete the strategy. Please try again.' })
        )
      });
  }

  private act(strategy: Strategy, call: ReturnType<StrategyService['disable']>, verb: string): void {
    if (!this.claims()) {
      return;
    }
    this.setBusy(strategy.strategyId, true);
    this.strategiesError.set('');
    call.pipe(finalize(() => this.setBusy(strategy.strategyId, false))).subscribe({
      next: (updated) => {
        this.strategies.update((list) => list.map((s) => (s.strategyId === updated.strategyId ? updated : s)));
        if (this.selectedId() === updated.strategyId) {
          this.loadExecutions(true);
        }
      },
      error: (error) => this.strategiesError.set(
        toUserFacingErrorMessage(error, { fallbackMessage: `Failed to ${verb} the strategy. Please try again.` })
      )
    });
  }

  private setBusy(strategyId: number, busy: boolean): void {
    this.busy.update((ids) => {
      const next = new Set(ids);
      if (busy) {
        next.add(strategyId);
      } else {
        next.delete(strategyId);
      }
      return next;
    });
  }

  /** @param silent a background refresh: keeps the list on screen and does not replace an error */
  private loadStrategies(silent = false): void {
    if (!this.claims()) {
      return;
    }
    if (!silent) {
      this.strategiesLoading.set(true);
      this.strategiesError.set('');
    }
    this.strategyService.list(this.claims()!.accountId)
      .pipe(finalize(() => this.strategiesLoading.set(false)))
      .subscribe({
        next: (list) => {
          this.strategies.set(list.strategies);
          this.maxStrategies.set(list.maxStrategies);
          if (this.selectedId() === null && list.strategies.length > 0) {
            this.select(list.strategies[0]);
          }
        },
        error: (error) => {
          if (!silent) {
            this.strategiesError.set(
              toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load your strategies. Please try again.' })
            );
          }
        }
      });
  }

  private loadExecutions(silent = false): void {
    const strategyId = this.selectedId();
    if (!this.claims() || strategyId === null) {
      return;
    }
    this.loadChart(strategyId, silent);
    if (!silent) {
      this.executionsLoading.set(true);
      this.executionsError.set('');
    }
    this.strategyService.executions(this.claims()!.accountId, strategyId)
      .pipe(finalize(() => this.executionsLoading.set(false)))
      .subscribe({
        next: (rows) => {
          if (this.selectedId() === strategyId) {
            this.executions.set(rows);
          }
        },
        error: (error) => {
          if (!silent) {
            this.executionsError.set(
              toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load the strategy activity.' })
            );
          }
        }
      });
  }

  private loadChart(strategyId: number, silent: boolean): void {
    if (!silent) {
      this.chartLoading.set(true);
      this.chartError.set('');
    }
    this.strategyService.chart(this.claims()!.accountId, strategyId)
      .pipe(finalize(() => this.chartLoading.set(false)))
      .subscribe({
        next: (chart) => {
          if (this.selectedId() === strategyId) {
            this.chart.set(chart);
          }
        },
        error: (error) => {
          if (!silent) {
            this.chartError.set(toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load the chart.' }));
          }
        }
      });
  }

  private loadInstruments(): void {
    if (!this.claims()) {
      return;
    }
    this.tradeService.getInstruments(this.claims()!.accountId).subscribe({
      next: (instruments) => {
        const sorted = [...instruments].sort((a, b) => a.symbol.localeCompare(b.symbol));
        this.instruments.set(sorted);
        if (!this.strategyForm.get('symbol')?.value && sorted.length > 0) {
          this.strategyForm.patchValue({ symbol: sorted[0].symbol });
        }
      },
      error: (error) => this.instrumentsError.set(
        toUserFacingErrorMessage(error, { fallbackMessage: 'Failed to load the instruments you can trade.' })
      )
    });
  }
}
