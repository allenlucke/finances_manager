import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { NeedsALookComponent } from './needs-a-look';
import {
  amountClass,
  firstOfThisMonth,
  money,
  monthLabel,
  shortDate,
  today,
} from '../../core/money';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  Account,
  LedgerEntity,
  MonthlyTotalsRow,
  NetWorthRow,
  ReconciliationRow,
  SpendRow,
} from '../../core/models';

/**
 * Net worth, account balances, this month's spending, and anything that fails to reconcile.
 *
 * <p>Every request has its own load state. The first version held bare arrays and rendered the
 * empty state when a request failed — "No accounts yet. Add one" on a 500 — and turned a missing
 * net worth into a confident `$0.00`. A failure and an empty ledger are different things and this
 * screen now says which it is looking at.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    NeedsALookComponent,
    MatCardModule,
    MatTableModule,
    MatIconModule,
    MatDividerModule,
    MatProgressBarModule,
    MatButtonModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent {
  private readonly api = inject(ApiClient);
  private readonly snackBar = inject(MatSnackBar);

  /**
   * The moment this dashboard last loaded. A `computed` over a bare `new Date()` evaluates once
   * and never again, so a tab left open across a month boundary kept the old month's label and
   * filter. Everything date-derived reads from this, and refresh() moves it.
   */
  protected readonly now = signal(new Date());

  protected readonly accounts = new LoadState<Account[]>('Could not load accounts.');
  protected readonly entities = new LoadState<LedgerEntity[]>('Could not load the sets of books.');
  protected readonly netWorth = new LoadState<NetWorthRow[]>('Could not load net worth.');
  protected readonly spend = new LoadState<SpendRow[]>('Could not load spending.');
  protected readonly totals = new LoadState<MonthlyTotalsRow[]>(
    "Could not load the month's totals.",
  );
  protected readonly reconciliation = new LoadState<ReconciliationRow[]>(
    'Could not check statements.',
  );

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly monthStart = computed(() => firstOfThisMonth(this.now()));
  protected readonly currentMonthLabel = computed(() => monthLabel(this.monthStart()));

  /**
   * The combined figure is the row with no entity; see v_net_worth's GROUPING SETS. Null, not
   * zero, when it has not arrived: `money(null)` renders a dash, and a dash is honest where
   * `$0.00` would be a claim.
   */
  protected readonly combined = computed(
    () => this.netWorth.value()?.find((row) => row.ledgerEntityId === null)?.netWorth ?? null,
  );

  /**
   * Beside the headline whenever any part of it is a holdings snapshot. The number is only honest
   * with its date: nothing in the ledger moves a brokerage balance between positions imports.
   */
  protected readonly snapshotNote = computed(() => {
    const row = this.netWorth.value()?.find((r) => r.ledgerEntityId === null);
    if (!row || row.snapshotAccounts === 0 || !row.oldestSnapshot) {
      return null;
    }
    const noun = row.snapshotAccounts === 1 ? 'brokerage account' : 'brokerage accounts';
    return `Includes ${row.snapshotAccounts} ${noun} valued as of ${shortDate(row.oldestSnapshot)}. Deposits since then count after the next positions import.`;
  });

  protected readonly perEntity = computed(() =>
    (this.netWorth.value() ?? [])
      .filter((row) => row.ledgerEntityId !== null)
      .map((row) => ({
        name: this.entities.value()?.find((e) => e.id === row.ledgerEntityId)?.name ?? 'Unknown',
        netWorth: row.netWorth,
      })),
  );

  protected readonly activeAccounts = computed(() =>
    (this.accounts.value() ?? []).filter((a) => a.active),
  );

  protected readonly thisMonth = computed(() => {
    const month = this.monthStart();
    return (this.spend.value() ?? [])
      .filter((row) => row.month === month && row.categoryKind === 'expense')
      .sort((a, b) => b.netAmount - a.netAmount);
  });

  /**
   * The month's denominator: everything that moved, not only what has a category. The spending
   * card below lists categorized rows, so a month that is mostly still in the review queue looked
   * cheap here with nothing to say so. The combined row (no entity) is the one to show.
   */
  protected readonly thisMonthTotals = computed(
    () =>
      this.totals
        .value()
        ?.find((row) => row.ledgerEntityId === null && row.month === this.monthStart()) ?? null,
  );

  /** Only statements that disagree with the ledger are worth surfacing. */
  protected readonly unreconciled = computed(() =>
    (this.reconciliation.value() ?? []).filter((row) => Number(row.difference) !== 0),
  );

  protected readonly anyError = computed(() =>
    [this.accounts, this.entities, this.netWorth, this.spend, this.totals, this.reconciliation]
      .map((state) => state.error())
      .filter((message): message is string => message !== null),
  );

  protected readonly loading = computed(
    () => this.accounts.loading() || this.netWorth.loading() || this.spend.loading(),
  );

  protected readonly accountColumns = ['name', 'type', 'balance'];

  constructor() {
    this.load();
  }

  /** Reloads everything and moves the clock, so the month label and filter follow the calendar. */
  protected refresh(): void {
    this.now.set(new Date());
    this.load();
  }

  private load(): void {
    // Fired together rather than chained: none of them depends on another's result, and the
    // dashboard should not take five round trips to appear.
    this.entities.run(this.api.entities());
    this.netWorth.run(this.api.netWorth());
    this.reconciliation.run(this.api.reconciliation());
    this.spend.run(this.api.spendVsTarget(this.monthStart(), today(this.now())));
    this.totals.run(this.api.monthlyTotals(this.monthStart(), today(this.now())));
    this.accounts.run(this.api.accounts());
  }

  /**
   * Removes a checkpoint no file will ever replace. A re-import replaces one it disagrees with on
   * its own; this is for the figure that came from a file read the wrong way round and has no
   * corrected twin. Importing the statement again recreates it.
   */
  protected removeCheckpoint(row: ReconciliationRow): void {
    this.api.deleteStatement(row.statementId).subscribe({
      next: () => {
        this.reconciliation.run(this.api.reconciliation());
        this.snackBar.open(
          'Checkpoint removed. Importing that statement again will record a fresh one.',
          undefined,
          { duration: 5000 },
        );
      },
      error: () =>
        this.snackBar.open('Could not remove that checkpoint.', undefined, { duration: 4000 }),
    });
  }

  protected accountName(id: number): string {
    return this.accounts.value()?.find((account) => account.id === id)?.name ?? 'an account';
  }

  /** Percentage of a target consumed, capped for the bar's sake but not for the number shown. */
  protected progress(row: SpendRow): number {
    if (!row.targetAmount || Number(row.targetAmount) === 0) {
      return 0;
    }
    return Math.min(100, (Number(row.netAmount) / Number(row.targetAmount)) * 100);
  }

  protected overBudget(row: SpendRow): boolean {
    return row.remaining !== null && Number(row.remaining) < 0;
  }
}
