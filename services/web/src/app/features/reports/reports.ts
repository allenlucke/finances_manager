import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { money } from '../../core/money';
import { LedgerEntity, YearReview } from '../../core/models';

/**
 * A year in review, per set of books or all of them: what came in and went out by category,
 * transfers excluded, and what is still uncategorized said before the totals — a year total that
 * quietly omits its review queue is the "cheap month" bug at annual scale.
 */
@Component({
  selector: 'app-reports',
  imports: [
    FormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class ReportsComponent {
  private readonly api = inject(ApiClient);

  protected readonly entities = new LoadState<LedgerEntity[]>('Could not load the sets of books.');
  protected readonly review = new LoadState<YearReview>('Could not load the year.');
  protected readonly year = signal(new Date().getFullYear());
  protected readonly entityId = signal<number | null>(null);
  protected readonly money = money;
  protected readonly columns = ['name', 'count', 'amount'];

  protected readonly years = computed(() => {
    const now = new Date().getFullYear();
    return [now, now - 1, now - 2, now - 3];
  });
  protected readonly income = computed(() =>
    (this.review.value()?.categories ?? []).filter((c) => c.kind === 'income'),
  );
  protected readonly expenses = computed(() =>
    (this.review.value()?.categories ?? []).filter((c) => c.kind === 'expense'),
  );

  constructor() {
    this.entities.run(this.api.entities());
    this.reload();
  }

  protected reload(): void {
    this.review.run(this.api.yearReview(this.year(), this.entityId()));
  }

  protected setYear(year: number): void {
    this.year.set(year);
    this.reload();
  }

  protected setEntity(id: number | null): void {
    this.entityId.set(id);
    this.reload();
  }

  protected csvHref(): string {
    return this.api.yearCsvHref(this.year(), this.entityId());
  }

  protected scope(): string {
    const r = this.review.value();
    return r?.entityName ?? 'all sets of books';
  }
}
