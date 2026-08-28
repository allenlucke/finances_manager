import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
import { amountClass, firstOfThisMonth, isoDate, money, today } from '../../core/money';
import { Account, Category, Direction, Transaction } from '../../core/models';

/** The ledger: browse a date range, add entries, recategorize, delete. */
@Component({
  selector: 'app-transactions',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss',
})
export class TransactionsComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly rows = signal<Transaction[]>([]);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly columns = ['date', 'description', 'account', 'category', 'amount', 'actions'];

  protected readonly range = this.forms.nonNullable.group({
    from: [new Date(new Date().getFullYear(), new Date().getMonth(), 1)],
    to: [new Date()],
  });

  protected readonly form = this.forms.nonNullable.group({
    accountId: [0, Validators.required],
    transactionDate: [new Date(), Validators.required],
    // Kept as a string all the way to the server so the value is never rounded by a JS number.
    // Money is NUMERIC(19,4) / BigDecimal on the other side.
    amount: ['', [Validators.required, Validators.pattern(/^\d+(\.\d{1,4})?$/)]],
    direction: ['debit' as Direction, Validators.required],
    description: ['', Validators.required],
    categoryId: [null as number | null],
    transfer: [false],
    transferAccountId: [null as number | null],
  });

  /**
   * Derived from the control's value *stream*, not its `.value`.
   *
   * <p>`computed(() => control.value)` looks right and is silently broken: a reactive form control
   * is not a signal, so there is nothing for `computed` to track — it evaluates once and never
   * again. The visible symptom was ticking "Transfer" and watching the Category field refuse to
   * change, which is the one interaction on this form that has to work.
   */
  protected readonly isTransfer = toSignal(this.form.controls.transfer.valueChanges, {
    initialValue: false,
  });

  private readonly selectedAccountId = toSignal(this.form.controls.accountId.valueChanges, {
    initialValue: this.form.controls.accountId.value,
  });

  /** Accounts other than the one selected — a transfer needs two distinct sides. */
  protected readonly otherAccounts = computed(() =>
    this.accounts().filter((account) => account.id !== this.selectedAccountId()),
  );

  protected readonly expenseCategories = computed(() =>
    this.categories().filter((category) => category.kind === 'expense'),
  );

  protected readonly incomeCategories = computed(() =>
    this.categories().filter((category) => category.kind === 'income'),
  );

  constructor() {
    this.api.accounts().subscribe((accounts) => {
      this.accounts.set(accounts);
      if (accounts.length) {
        this.form.patchValue({ accountId: accounts[0].id });
      }
    });
    this.api.categories().subscribe((categories) => this.categories.set(categories));

    // Clearing the category when marking a transfer mirrors the server, which refuses a
    // categorized transfer outright — the budget was already charged at purchase.
    this.form.controls.transfer.valueChanges.subscribe((isTransfer) => {
      if (isTransfer) {
        this.form.patchValue({ categoryId: null }, { emitEvent: false });
      } else {
        this.form.patchValue({ transferAccountId: null }, { emitEvent: false });
      }
    });

    this.range.valueChanges.subscribe(() => this.reload());
    this.reload();
  }

  protected accountName(id: number | null): string {
    if (id === null) return '—';
    return this.accounts().find((account) => account.id === id)?.name ?? '—';
  }

  protected categoryName(id: number | null): string {
    if (id === null) return '';
    return this.categories().find((category) => category.id === id)?.name ?? '';
  }

  protected reload(): void {
    const { from, to } = this.range.getRawValue();
    this.loading.set(true);
    this.api.transactions(isoDate(from), isoDate(to), 0, 200).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    if (value.transfer && !value.transferAccountId) {
      this.snackBar.open('Choose the account the money moved to.', undefined, { duration: 3500 });
      return;
    }

    this.saving.set(true);
    this.api
      .createTransaction({
        accountId: value.accountId,
        transactionDate: isoDate(value.transactionDate),
        amount: value.amount,
        direction: value.direction,
        description: value.description,
        categoryId: value.transfer ? null : value.categoryId,
        transfer: value.transfer,
        transferAccountId: value.transfer ? value.transferAccountId : null,
      })
      .subscribe({
        next: (legs) => {
          this.saving.set(false);
          // Reset the emptied controls rather than patching them. patchValue leaves each control
          // touched, so "required" errors flash red on fields the user just submitted successfully
          // — it reads as though the save failed. Written out rather than looped because the
          // controls have different value types.
          this.form.controls.amount.reset('');
          this.form.controls.description.reset('');
          this.form.controls.categoryId.reset(null);
          this.reload();
          this.snackBar.open(
            legs.length > 1 ? 'Transfer recorded on both accounts' : 'Transaction added',
            undefined,
            { duration: 2500 },
          );
        },
        error: () => {
          this.saving.set(false);
          this.snackBar.open('Could not save the transaction.', undefined, { duration: 4000 });
        },
      });
  }

  protected recategorize(row: Transaction, categoryId: number | null): void {
    this.api.categorize(row.id, categoryId).subscribe({
      next: () => this.reload(),
      error: (error) => {
        // 422 is the double-count rule: a transfer may never carry a category.
        this.snackBar.open(
          error?.status === 422
            ? 'Transfers are not categorized — the budget was charged when the purchase happened.'
            : 'Could not change the category.',
          undefined,
          { duration: 5000 },
        );
      },
    });
  }

  protected remove(row: Transaction): void {
    this.api.deleteTransaction(row.id).subscribe({
      next: () => {
        this.reload();
        this.snackBar.open(
          row.transferGroupId ? 'Transfer removed from both accounts' : 'Transaction removed',
          undefined,
          { duration: 2500 },
        );
      },
      error: () => this.snackBar.open('Could not remove it.', undefined, { duration: 4000 }),
    });
  }
}
