import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { amountClass, isoDate, money, monthLabel } from '../../core/money';
import { Category, CategoryKind, LedgerEntity, SpendRow, Target } from '../../core/models';

/**
 * Categories and targets.
 *
 * <p>Targets are deliberately optional and the UI says so. Most categories are better served by a
 * baseline derived from history (M3/M6); a target exists to express intent that contradicts
 * history, like deciding to spend less than you have been.
 */
@Component({
  selector: 'app-budget',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './budget.html',
  styleUrl: './budget.scss',
})
export class BudgetComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly categories = new LoadState<Category[]>('Could not load categories.');
  protected readonly entities = new LoadState<LedgerEntity[]>('Could not load the sets of books.');
  protected readonly targets = new LoadState<Target[]>('Could not load targets.');
  protected readonly spend = new LoadState<SpendRow[]>('Could not load spending history.');
  protected readonly saving = signal(false);

  protected readonly money = money;
  protected readonly amountClass = amountClass;
  protected readonly monthLabel = monthLabel;

  protected readonly cadences = [
    { value: 'weekly', label: 'a week' },
    { value: 'monthly', label: 'a month' },
    { value: 'quarterly', label: 'a quarter' },
    { value: 'yearly', label: 'a year' },
  ];

  protected readonly targetColumns = ['category', 'entity', 'amount', 'from', 'to'];
  protected readonly historyColumns = ['month', 'category', 'spent', 'target', 'remaining'];
  protected readonly incomeColumns = ['month', 'category', 'received', 'expected'];
  protected readonly categoryColumns = ['name', 'kind'];

  /** Expenses first, then income — the order they are thought about. */
  protected readonly sortedCategories = computed(() =>
    [...(this.categories.value() ?? [])].sort(
      (a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name),
    ),
  );

  protected readonly categoryForm = this.forms.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    kind: ['expense' as CategoryKind, Validators.required],
  });

  protected readonly targetForm = this.forms.nonNullable.group({
    categoryId: [0, Validators.required],
    ledgerEntityId: [0, Validators.required],
    amount: ['', [Validators.required, Validators.pattern(/^\d+(\.\d{1,4})?$/)]],
    cadence: ['monthly', Validators.required],
    // Defaults to today, and can be changed. It used to be hardcoded to the 1st of the month
    // and hidden, which meant a second target in the same month was refused with a message
    // about a date the person had never seen and could not change.
    effectiveFrom: [new Date() as Date | null, Validators.required],
  });

  /** Only the currently-open target per category; closed rows are history. */
  protected readonly activeTargets = computed(() =>
    (this.targets.value() ?? []).filter((target) => target.effectiveTo === null),
  );

  protected readonly pastTargets = computed(() =>
    (this.targets.value() ?? []).filter((target) => target.effectiveTo !== null),
  );

  /**
   * Expense rows only. The spend view reports income as a negative "spend" (it flips the sign so
   * that positive means spent), and an unfiltered table showed a paycheque as Spent −$3,000 with
   * Remaining $6,000. Income gets its own table with its own words.
   */
  protected readonly recentSpend = computed(() =>
    [...(this.spend.value() ?? [])]
      .filter((row) => row.categoryKind === 'expense')
      .sort((a, b) => b.month.localeCompare(a.month))
      .slice(0, 40),
  );

  protected readonly recentIncome = computed(() =>
    [...(this.spend.value() ?? [])]
      .filter((row) => row.categoryKind === 'income')
      .sort((a, b) => b.month.localeCompare(a.month))
      .slice(0, 24),
  );

  constructor() {
    this.entities.run(this.api.entities(), (entities) => {
      const personal = entities.find((entity) => entity.kind === 'personal') ?? entities[0];
      if (personal) {
        this.targetForm.patchValue({ ledgerEntityId: personal.id });
      }
    });
    this.reload();
  }

  protected categoryName(id: number): string {
    return this.categories.value()?.find((category) => category.id === id)?.name ?? '—';
  }

  protected entityName(id: number): string {
    return this.entities.value()?.find((entity) => entity.id === id)?.name ?? '—';
  }

  protected cadenceLabel(cadence: string): string {
    return this.cadences.find((c) => c.value === cadence)?.label ?? cadence;
  }

  protected addCategory(): void {
    if (this.categoryForm.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const value = this.categoryForm.getRawValue();

    this.api.createCategory({ name: value.name, kind: value.kind }).subscribe({
      next: () => {
        this.saving.set(false);
        // reset(), not patchValue(): patching leaves the control touched, so a "required" error
        // flashes red on a field the user just successfully submitted.
        this.categoryForm.reset({ name: '', kind: value.kind });
        this.reload();
        this.snackBar.open('Category added', undefined, { duration: 2500 });
      },
      error: (error: { status?: number }) => {
        this.saving.set(false);
        this.snackBar.open(
          error?.status === 409 ? 'That category already exists.' : 'Could not add the category.',
          undefined,
          { duration: 4000 },
        );
      },
    });
  }

  protected setTarget(): void {
    if (this.targetForm.invalid || this.saving()) {
      return;
    }
    const value = this.targetForm.getRawValue();
    const from = isoDate(value.effectiveFrom);
    if (from === null) {
      this.snackBar.open('Choose a real start date.', undefined, { duration: 3500 });
      return;
    }
    this.saving.set(true);

    this.api
      .setTarget({
        categoryId: value.categoryId,
        ledgerEntityId: value.ledgerEntityId,
        amount: value.amount,
        cadence: value.cadence,
        effectiveFrom: from,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.targetForm.reset({
            categoryId: value.categoryId,
            ledgerEntityId: value.ledgerEntityId,
            amount: '',
            cadence: value.cadence,
            effectiveFrom: new Date(),
          });
          this.reload();
          // Says what actually happened: the old row was closed, not overwritten.
          this.snackBar.open('Target set. The previous one was kept as history.', undefined, {
            duration: 3500,
          });
        },
        error: (error: { status?: number }) => {
          this.saving.set(false);
          this.snackBar.open(
            error?.status === 409
              ? `A target for this category already starts on or after ${from}. Pick a later start date to replace it.`
              : 'Could not set the target.',
            undefined,
            { duration: 6000 },
          );
        },
      });
  }

  private reload(): void {
    this.categories.run(this.api.categories(), (categories) => {
      const expense = categories.find((category) => category.kind === 'expense');
      if (expense && !this.targetForm.controls.categoryId.value) {
        this.targetForm.patchValue({ categoryId: expense.id });
      }
    });
    this.targets.run(this.api.targets());
    this.spend.run(this.api.spendVsTarget());
  }
}
