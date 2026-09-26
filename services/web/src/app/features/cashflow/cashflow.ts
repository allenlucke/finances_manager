import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { money } from '../../core/money';
import { CashflowReport, RecurringSeries } from '../../core/models';

/**
 * The ledger's rhythm (M9, D-21): recurring charges found in the history, with their evidence;
 * what is expected in the weeks ahead; and what looks wrong. Nothing here is declared by hand and
 * nothing is stored; it is a way of reading the ledger.
 */
@Component({
  selector: 'app-cashflow',
  imports: [MatCardModule, MatTableModule, MatButtonModule, MatButtonToggleModule, MatIconModule],
  templateUrl: './cashflow.html',
  styleUrl: './cashflow.scss',
})
export class CashflowComponent {
  private readonly api = inject(ApiClient);

  protected readonly report = new LoadState<CashflowReport>(
    'Could not read the ledger for recurring charges.',
  );
  protected readonly days = signal(30);
  protected readonly money = money;

  protected readonly upcomingColumns = ['date', 'what', 'account', 'amount'];
  protected readonly seriesColumns = ['what', 'cadence', 'typical', 'last', 'next', 'status'];

  protected readonly missing = computed(() =>
    (this.report.value()?.series ?? []).filter((s) => s.status === 'missing'),
  );
  protected readonly net = computed(() => {
    const r = this.report.value();
    return r ? r.expectedIn - r.expectedOut : null;
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.report.run(this.api.cashflow(this.days()));
  }

  protected setDays(days: number): void {
    this.days.set(days);
    this.reload();
  }

  protected signed(amount: number, direction: string): number {
    return direction === 'debit' ? -amount : amount;
  }

  protected statusWords(s: RecurringSeries): string {
    switch (s.status) {
      case 'missing':
        return `missing — expected around ${s.nextExpected}, not seen since ${s.lastDate}`;
      case 'upcoming':
        return `expected ${s.nextExpected}`;
      default:
        return `on track, next ${s.nextExpected}`;
    }
  }
}
