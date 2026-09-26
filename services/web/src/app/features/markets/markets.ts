import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { amountClass, money } from '../../core/money';
import {
  AlertEvent,
  AlertRule,
  HoldingAtMarket,
  MarketStatus,
  PriceAlert,
  QuoteRow,
  WatchlistEntry,
} from '../../core/models';

/**
 * Watching the market (M7a): the watchlist with its latest quotes, price alerts and their
 * firings, and every holding valued at the latest quote beside the value its snapshot gave it.
 *
 * <p>A price is not money. Nothing on this screen is summed into a balance; the "at last quote"
 * figures are labelled as a moment and the snapshot figures as what the broker asserted. A quote
 * from the fake provider says so on every row.
 */
@Component({
  selector: 'app-markets',
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
  templateUrl: './markets.html',
  styleUrl: './markets.scss',
})
export class MarketsComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly status = new LoadState<MarketStatus>(
    'Could not check the market-data service.',
  );
  protected readonly watchlist = new LoadState<WatchlistEntry[]>('Could not load the watchlist.');
  protected readonly quotes = new LoadState<QuoteRow[]>('Could not load quotes.');
  protected readonly holdings = new LoadState<HoldingAtMarket[]>('Could not load holdings.');
  protected readonly alerts = new LoadState<PriceAlert[]>('Could not load alerts.');
  protected readonly events = new LoadState<AlertEvent[]>('Could not load alert history.');
  protected readonly refreshing = signal(false);
  protected readonly saving = signal(false);

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly rules: { value: AlertRule; label: string; unit: string }[] = [
    { value: 'above', label: 'rises above', unit: 'price' },
    { value: 'below', label: 'falls below', unit: 'price' },
    { value: 'pct_move', label: 'moves more than', unit: '% today' },
  ];

  protected readonly quoteColumns = ['symbol', 'price', 'change', 'asOf', 'actions'];
  protected readonly holdingColumns = [
    'account',
    'symbol',
    'quantity',
    'snapshot',
    'live',
    'quoteAsOf',
  ];
  protected readonly alertColumns = ['symbol', 'rule', 'state', 'lastFired', 'actions'];
  protected readonly eventColumns = ['firedAt', 'message', 'delivered'];

  protected readonly watchForm = this.forms.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9.\-]{0,15}$/)]],
    note: [''],
  });

  protected readonly alertForm = this.forms.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9.\-]{0,15}$/)]],
    rule: ['above' as AlertRule, Validators.required],
    // A string all the way to the server, like every amount in this app.
    threshold: ['', [Validators.required, Validators.pattern(/^\d+(\.\d{1,6})?$/)]],
    note: [''],
  });

  /** Quotes that are only watched, listed first; held ones appear in the holdings table too. */
  protected readonly quoteRows = computed(() => this.quotes.value() ?? []);

  /** A fake provider is a stand-in for a stack with no vendor. It must never read as market data. */
  protected readonly fakeData = computed(
    () =>
      this.status.value()?.provider === 'fake' ||
      (this.quotes.value() ?? []).some((q) => q.source === 'fake'),
  );

  protected readonly off = computed(() => {
    const status = this.status.value();
    return status !== null && !status.available;
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.status.run(this.api.marketStatus());
    this.watchlist.run(this.api.watchlist());
    this.quotes.run(this.api.quotes());
    this.holdings.run(this.api.holdingsAtMarket());
    this.alerts.run(this.api.priceAlerts());
    this.events.run(this.api.alertEvents(20));
  }

  protected refresh(): void {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.api.refreshQuotes().subscribe({
      next: (result) => {
        this.refreshing.set(false);
        this.reload();
        const fired = result.alertsFired ? `, ${result.alertsFired} alert(s) fired` : '';
        const warned = result.warnings.length ? ` — ${result.warnings[0]}` : '';
        this.snackBar.open(
          `${result.fetched} quote(s) from ${result.provider}${fired}${warned}`,
          undefined,
          { duration: 6000 },
        );
      },
      error: (error: { status?: number; error?: { detail?: string | null } | null }) => {
        this.refreshing.set(false);
        this.snackBar.open(
          error?.error?.detail ||
            (error?.status === 503 ? 'Market data is off.' : 'Could not refresh quotes.'),
          undefined,
          { duration: 7000 },
        );
      },
    });
  }

  protected watch(): void {
    if (this.watchForm.invalid || this.saving()) return;
    const value = this.watchForm.getRawValue();
    this.saving.set(true);
    this.api.watch(value.symbol.toUpperCase(), value.note || null).subscribe({
      next: (entry) => {
        this.saving.set(false);
        this.watchForm.reset({ symbol: '', note: '' });
        this.reload();
        this.snackBar.open(
          `Watching ${entry.symbol}. Refresh to fetch its first quote.`,
          undefined,
          {
            duration: 4000,
          },
        );
      },
      error: (error: { error?: { detail?: string | null } | null }) => {
        this.saving.set(false);
        this.snackBar.open(error?.error?.detail || 'Could not add that symbol.', undefined, {
          duration: 5000,
        });
      },
    });
  }

  protected unwatch(row: QuoteRow): void {
    const entry = (this.watchlist.value() ?? []).find((w) => w.securityId === row.securityId);
    if (!entry) return;
    this.api.unwatch(entry.id).subscribe({
      next: () => this.reload(),
      error: () =>
        this.snackBar.open('Could not stop watching that.', undefined, { duration: 4000 }),
    });
  }

  protected addAlert(): void {
    if (this.alertForm.invalid || this.saving()) return;
    const value = this.alertForm.getRawValue();
    this.saving.set(true);
    this.api
      .addPriceAlert({
        symbol: value.symbol.toUpperCase(),
        rule: value.rule,
        threshold: value.threshold,
        note: value.note || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.alertForm.reset({ symbol: '', rule: value.rule, threshold: '', note: '' });
          this.reload();
          this.snackBar.open(
            'Alert set. It fires once when the condition becomes true.',
            undefined,
            {
              duration: 4000,
            },
          );
        },
        error: (error: { error?: { detail?: string | null } | null }) => {
          this.saving.set(false);
          this.snackBar.open(error?.error?.detail || 'Could not set the alert.', undefined, {
            duration: 5000,
          });
        },
      });
  }

  protected toggleAlert(alert: PriceAlert, active: boolean): void {
    this.api.setAlertActive(alert.id, active).subscribe({
      next: () => this.reload(),
      error: () => {
        this.snackBar.open('Could not change that alert.', undefined, { duration: 4000 });
        this.reload();
      },
    });
  }

  protected deleteAlert(alert: PriceAlert): void {
    this.api.deletePriceAlert(alert.id).subscribe({
      next: () => this.reload(),
      error: () =>
        this.snackBar.open('Could not remove that alert.', undefined, { duration: 4000 }),
    });
  }

  protected describe(alert: PriceAlert): string {
    const rule = this.rules.find((r) => r.value === alert.rule);
    const threshold = alert.rule === 'pct_move' ? `${alert.threshold}%` : money(alert.threshold);
    return `${rule?.label ?? alert.rule} ${threshold}`;
  }

  /** Percent with its sign, so colour is never the only cue. */
  protected pct(value: number | null): string {
    if (value === null || value === undefined) return '—';
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(2)}%`;
  }

  protected pctClass(value: number | null): string {
    return amountClass(value);
  }
}
