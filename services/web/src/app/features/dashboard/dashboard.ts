import { Component, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ApiClient } from '../../core/api';
import { amountClass, firstOfThisMonth, money, monthLabel, today } from '../../core/money';
import { Account, LedgerEntity, NetWorthRow, ReconciliationRow, SpendRow } from '../../core/models';

/** Net worth, account balances, this month's spending, and anything that fails to reconcile. */
@Component({
  selector: 'app-dashboard',
  imports: [
    MatCardModule,
    MatTableModule,
    MatIconModule,
    MatDividerModule,
    MatProgressBarModule,
    RouterLink,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent {
  private readonly api = inject(ApiClient);

  protected readonly loading = signal(true);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly entities = signal<LedgerEntity[]>([]);
  protected readonly netWorth = signal<NetWorthRow[]>([]);
  protected readonly spend = signal<SpendRow[]>([]);
  protected readonly reconciliation = signal<ReconciliationRow[]>([]);

  protected readonly money = money;
  protected readonly amountClass = amountClass;
  protected readonly monthLabel = monthLabel;
  /** "August 2026" — the month this dashboard covers, whether or not anything landed in it. */
  protected readonly currentMonthLabel = computed(() => monthLabel(firstOfThisMonth()));

  /** The combined figure is the row with no entity; see v_net_worth's GROUPING SETS. */
  protected readonly combined = computed(
    () => this.netWorth().find((row) => row.ledgerEntityId === null)?.netWorth ?? 0,
  );

  protected readonly perEntity = computed(() =>
    this.netWorth()
      .filter((row) => row.ledgerEntityId !== null)
      .map((row) => ({
        name: this.entities().find((e) => e.id === row.ledgerEntityId)?.name ?? 'Unknown',
        netWorth: row.netWorth,
      })),
  );

  protected readonly activeAccounts = computed(() => this.accounts().filter((a) => a.active));

  protected readonly thisMonth = computed(() => {
    const month = firstOfThisMonth();
    return this.spend()
      .filter((row) => row.month === month && row.categoryKind === 'expense')
      .sort((a, b) => b.netAmount - a.netAmount);
  });

  /** Only statements that disagree with the ledger are worth surfacing. */
  protected readonly unreconciled = computed(() =>
    this.reconciliation().filter((row) => Number(row.difference) !== 0),
  );

  protected readonly accountColumns = ['name', 'type', 'balance'];
  protected readonly spendColumns = ['category', 'spent', 'target', 'remaining'];

  constructor() {
    this.load();
  }

  private load(): void {
    // Fired together rather than chained: none of them depends on another's result, and the
    // dashboard should not take four round trips to appear.
    this.api.entities().subscribe((entities) => this.entities.set(entities));
    this.api.netWorth().subscribe((rows) => this.netWorth.set(rows));
    this.api.reconciliation().subscribe((rows) => this.reconciliation.set(rows));
    this.api.spendVsTarget(firstOfThisMonth(), today()).subscribe((rows) => this.spend.set(rows));
    this.api.accounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
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
