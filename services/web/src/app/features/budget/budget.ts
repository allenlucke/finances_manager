import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiClient } from '../../core/api';
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
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './budget.html',
  styleUrl: './budget.scss',
})
export class BudgetComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly categories = signal<Category[]>([]);
  protected readonly entities = signal<LedgerEntity[]>([]);
  protected readonly targets = signal<Target[]>([]);
  protected readonly spend = signal<SpendRow[]>([]);
  protected readonly saving = signal(false);

  protected readonly money = money;
  protected readonly amountClass = amountClass;
  protected readonly monthLabel = monthLabel;

  protected readonly targetColumns = ['category', 'entity', 'amount', 'from', 'to'];
  protected readonly historyColumns = ['month', 'category', 'spent', 'target', 'remaining'];
  protected readonly categoryColumns = ['name', 'kind'];

  /** Expenses first, then income — the order they are thought about. */
  protected readonly sortedCategories = computed(() =>
    [...this.categories()].sort(
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
  });

  /** Only the currently-open target per category; closed rows are history. */
  protected readonly activeTargets = computed(() =>
    this.targets().filter((target) => target.effectiveTo === null),
  );

  protected readonly pastTargets = computed(() =>
    this.targets().filter((target) => target.effectiveTo !== null),
  );

  protected readonly recentSpend = computed(() =>
    [...this.spend()].sort((a, b) => b.month.localeCompare(a.month)).slice(0, 40),
  );

  constructor() {
    this.api.entities().subscribe((entities) => {
      this.entities.set(entities);
      const personal = entities.find((entity) => entity.kind === 'personal') ?? entities[0];
      if (personal) {
        this.targetForm.patchValue({ ledgerEntityId: personal.id });
      }
    });
    this.reload();
  }

  protected categoryName(id: number): string {
    return this.categories().find((category) => category.id === id)?.name ?? '—';
  }

  protected entityName(id: number): string {
    return this.entities().find((entity) => entity.id === id)?.name ?? '—';
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
      error: (error) => {
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
    this.saving.set(true);
    const value = this.targetForm.getRawValue();

    this.api
      .setTarget({
        categoryId: value.categoryId,
        ledgerEntityId: value.ledgerEntityId,
        amount: value.amount,
        // Effective from the first of this month, so the current month is governed by the number
        // just entered rather than starting partway through.
        effectiveFrom: isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.targetForm.reset({
            categoryId: value.categoryId,
            ledgerEntityId: value.ledgerEntityId,
            amount: '',
          });
          this.reload();
          // Says what actually happened: the old row was closed, not overwritten.
          this.snackBar.open('Target set. The previous one was kept as history.', undefined, {
            duration: 3500,
          });
        },
        error: (error) => {
          this.saving.set(false);
          this.snackBar.open(
            error?.status === 409
              ? 'A target for this category already starts on or after that date.'
              : 'Could not set the target.',
            undefined,
            { duration: 4500 },
          );
        },
      });
  }

  private reload(): void {
    this.api.categories().subscribe((categories) => {
      this.categories.set(categories);
      const expense = categories.find((category) => category.kind === 'expense');
      if (expense && !this.targetForm.controls.categoryId.value) {
        this.targetForm.patchValue({ categoryId: expense.id });
      }
    });
    this.api.targets().subscribe((targets) => this.targets.set(targets));
    this.api.spendVsTarget().subscribe((rows) => this.spend.set(rows));
  }
}
