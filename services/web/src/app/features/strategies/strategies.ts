import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  FormBuilder,
  FormControl,
  FormRecord,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { isoDate, money, today } from '../../core/money';
import {
  BacktestRun,
  MarketStatus,
  SavedStrategy,
  StrategyInfo,
  StrategyOutcome,
  TIMEFRAMES,
  Timeframe,
} from '../../core/models';

type Detail = { status?: number; error?: { detail?: string | null } | null };

/**
 * Strategies and backtests (M7c, D-19).
 *
 * <p>A backtest is a claim, and this screen shows it the way the service makes it: the warnings
 * first, buy-and-hold beside the strategy's return, the out-of-sample half beside the in-sample
 * one. A saved strategy that is switched on proposes <em>drafts</em> on a timer; the Orders card
 * on the Markets screen is where they wait for a person. Nothing here trades.
 */
@Component({
  selector: 'app-strategies',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatSlideToggleModule,
  ],
  templateUrl: './strategies.html',
  styleUrl: './strategies.scss',
})
export class StrategiesComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly market = new LoadState<MarketStatus>(
    'Could not check the market-data service.',
  );
  protected readonly catalog = new LoadState<StrategyInfo[]>('Could not load the strategy list.');
  protected readonly strategies = new LoadState<SavedStrategy[]>('Could not load strategies.');
  protected readonly runs = new LoadState<BacktestRun[]>('Could not load past backtests.');
  protected readonly running = signal(false);
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);
  /** The run on display: the one just run, or one chosen from the list. */
  protected readonly shown = signal<BacktestRun | null>(null);
  protected readonly outcomes = signal<StrategyOutcome[] | null>(null);
  protected readonly saving = signal(false);

  protected readonly money = money;
  protected readonly timeframes = TIMEFRAMES;
  protected readonly strategyColumns = ['name', 'rule', 'shares', 'active', 'last', 'actions'];
  protected readonly runColumns = ['when', 'what', 'period', 'return', 'oos', 'trades', 'actions'];
  protected readonly tradeColumns = ['in', 'out', 'quantity', 'prices', 'pnl', 'why'];

  protected readonly form = this.forms.nonNullable.group({
    kind: ['sma_cross', Validators.required],
    symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9.\-]{0,15}$/)]],
    timeframe: ['1Day' as Timeframe, Validators.required],
    start: [isoDate(new Date(Date.now() - 365 * 24 * 3600 * 1000)) ?? '', Validators.required],
    end: [today(), Validators.required],
    initialCash: ['10000', [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)]],
    slippageBps: ['5', [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)]],
    commission: ['0', [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)]],
    outOfSampleFraction: ['0.3', [Validators.required, Validators.pattern(/^0(\.\d{1,2})?$/)]],
  });
  /** One control per parameter of the chosen kind, rebuilt when the kind changes. */
  protected readonly params = new FormRecord<FormControl<string>>({});
  protected readonly saveForm = this.forms.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    quantity: ['1', [Validators.required, Validators.pattern(/^(?!0+(\.0+)?$)\d+(\.\d{1,8})?$/)]],
    notes: [''],
  });

  protected readonly kind = signal<string>('sma_cross');
  protected readonly current = computed(() =>
    (this.catalog.value() ?? []).find((c) => c.kind === this.kind()),
  );
  protected readonly allowedTimeframes = computed(() =>
    this.current()?.intraday ? this.timeframes.filter((t) => t !== '1Day') : this.timeframes,
  );
  protected readonly off = computed(() => {
    const status = this.market.value();
    return status !== null && !status.available;
  });
  protected readonly fake = computed(() => this.market.value()?.provider === 'fake');

  constructor() {
    this.reload();
    this.form.controls.kind.valueChanges.subscribe((kind) => this.chooseKind(kind));
  }

  protected reload(): void {
    this.market.run(this.api.marketStatus());
    this.catalog.run(this.api.strategyCatalog(), () =>
      this.chooseKind(this.form.controls.kind.value),
    );
    this.strategies.run(this.api.strategies());
    this.runs.run(this.api.backtests(50));
  }

  protected chooseKind(kind: string): void {
    this.kind.set(kind);
    for (const name of Object.keys(this.params.controls)) {
      this.params.removeControl(name);
    }
    const info = this.current();
    for (const p of info?.params ?? []) {
      this.params.addControl(
        p.name,
        new FormControl(p.default, {
          nonNullable: true,
          validators: [Validators.required, Validators.pattern(/^-?\d+(\.\d{1,6})?$/)],
        }),
      );
    }
    if (info?.intraday && this.form.controls.timeframe.value === '1Day') {
      this.form.controls.timeframe.setValue('15Min');
    }
  }

  protected paramValues(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [name, control] of Object.entries(this.params.controls)) {
      out[name] = control.value;
    }
    return out;
  }

  protected run(): void {
    if (this.form.invalid || this.params.invalid || this.running() || this.off()) return;
    const v = this.form.getRawValue();
    this.running.set(true);
    this.api
      .runBacktest({
        kind: v.kind,
        params: this.paramValues(),
        symbol: v.symbol.toUpperCase(),
        timeframe: v.timeframe,
        start: v.start,
        end: v.end,
        initialCash: v.initialCash,
        slippageBps: v.slippageBps,
        commission: v.commission,
        outOfSampleFraction: v.outOfSampleFraction,
        strategyId: null,
      })
      .subscribe({
        next: (run) => {
          this.running.set(false);
          this.notice.set(null);
          this.shown.set(run);
          this.saveForm.patchValue({ name: `${this.current()?.label ?? v.kind} on ${run.symbol}` });
          this.runs.run(this.api.backtests(50));
        },
        error: (error: Detail) => {
          this.running.set(false);
          this.notice.set(
            error?.error?.detail ||
              (error?.status === 503 ? 'Market data is off.' : 'The backtest could not be run.'),
          );
        },
      });
  }

  protected show(run: BacktestRun): void {
    this.api.backtest(run.id).subscribe({
      next: (full) => {
        this.shown.set(full);
        this.saveForm.patchValue({ name: `${this.labelOf(full.kind)} on ${full.symbol}` });
      },
      error: () => this.notice.set('Could not load that backtest.'),
    });
  }

  protected deleteRun(run: BacktestRun): void {
    this.api.deleteBacktest(run.id).subscribe({
      next: () => {
        if (this.shown()?.id === run.id) this.shown.set(null);
        this.runs.run(this.api.backtests(50));
      },
      error: () => this.notice.set('Could not remove that backtest.'),
    });
  }

  /** Saves the shown run's rule as a strategy, with the person's size for live proposals. */
  protected saveShown(): void {
    const run = this.shown();
    if (!run || this.saveForm.invalid || this.saving()) return;
    const v = this.saveForm.getRawValue();
    this.saving.set(true);
    this.api
      .saveStrategy({
        name: v.name,
        kind: run.kind,
        params: run.params,
        symbol: run.symbol,
        timeframe: run.timeframe,
        quantity: v.quantity,
        notes: v.notes || null,
      })
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.notice.set(null);
          this.strategies.run(this.api.strategies());
          this.snackBar.open(
            `Saved "${saved.name}". It is off until you switch it on; on, it proposes drafts, never orders.`,
            undefined,
            { duration: 7000 },
          );
        },
        error: (error: Detail) => {
          this.saving.set(false);
          this.notice.set(error?.error?.detail || 'Could not save the strategy.');
        },
      });
  }

  protected toggle(strategy: SavedStrategy, active: boolean): void {
    this.api.setStrategyActive(strategy.id, active).subscribe({
      next: () => this.strategies.run(this.api.strategies()),
      error: () => {
        this.notice.set('Could not change that strategy.');
        this.strategies.run(this.api.strategies());
      },
    });
  }

  protected remove(strategy: SavedStrategy): void {
    this.api.deleteStrategy(strategy.id).subscribe({
      next: () => this.strategies.run(this.api.strategies()),
      error: () => this.notice.set('Could not remove that strategy.'),
    });
  }

  protected evaluate(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.evaluateStrategies().subscribe({
      next: (outcomes) => {
        this.busy.set(false);
        this.notice.set(null);
        this.outcomes.set(outcomes);
        this.strategies.run(this.api.strategies());
      },
      error: (error: Detail) => {
        this.busy.set(false);
        this.notice.set(
          error?.error?.detail ||
            (error?.status === 503 ? 'Market data is off.' : 'Could not evaluate strategies.'),
        );
      },
    });
  }

  protected labelOf(kind: string): string {
    return (this.catalog.value() ?? []).find((c) => c.kind === kind)?.label ?? kind;
  }

  protected describeRule(kind: string, params: Record<string, string>): string {
    const entries = Object.entries(params ?? {});
    return entries.length
      ? `${this.labelOf(kind)} (${entries.map(([k, v]) => `${k} ${v}`).join(', ')})`
      : this.labelOf(kind);
  }

  protected outcomeText(o: StrategyOutcome): string {
    if (o.orderId !== null && o.action) {
      return `${o.name}: proposed draft #${o.orderId}, ${o.action} ${o.symbol} — ${o.reason}. Confirm it on the Markets screen.`;
    }
    return `${o.name}: ${o.note}${o.reason ? ` (${o.reason})` : ''}.`;
  }

  /** Percent with its sign, so colour is never the only cue. */
  protected pct(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '—';
    const n = Number(value);
    if (Number.isNaN(n)) return '—';
    return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`;
  }

  protected pctClass(value: number | string | null | undefined): string {
    const n = Number(value);
    if (value === null || value === undefined || Number.isNaN(n) || n === 0) return '';
    return n > 0 ? 'amount-positive' : 'amount-negative';
  }

  /** A drawdown is shown as the loss it was. */
  protected drawdown(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '—';
    return this.pct(-Math.abs(Number(value)));
  }

  /** Decimals arrive from the service as strings; money() wants a number. */
  protected dollars(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') return '—';
    return money(Number(value));
  }

  protected inSampleShare(run: BacktestRun): string {
    return `${Math.round((1 - run.outOfSampleFraction) * 100)}%`;
  }

  protected heldOutShare(run: BacktestRun): string {
    return `${Math.round(run.outOfSampleFraction * 100)}%`;
  }

  /** Beats or trails: the sentence beside the two returns. */
  protected verdict(run: BacktestRun): string {
    if (run.totalReturnPct > run.benchmarkReturnPct) {
      return 'beat buy and hold on these bars';
    }
    return 'did not beat buy and hold on these bars';
  }

  /** An SVG polyline of the equity curve, scaled to a 100×40 box. */
  protected curvePoints(run: BacktestRun): string {
    const curve = run.result?.equity_curve ?? [];
    if (curve.length < 2) return '';
    const values = curve.map((p) => Number(p.equity));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    return values
      .map((v, i) => {
        const x = (i / (values.length - 1)) * 100;
        const y = 40 - ((v - min) / span) * 36 - 2;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  }
}
