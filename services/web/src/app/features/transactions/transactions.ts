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
import { ActivatedRoute } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiClient } from '../../core/api';
import { LoadState } from '../../core/load-state';
import { amountClass, isoDate, money } from '../../core/money';
import { Account, Category, Direction, Page, Transaction } from '../../core/models';

/** The ledger: browse a date range, add entries, recategorize, delete, and undo a delete. */
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
  private readonly route = inject(ActivatedRoute);

  /** The most rows one request asks for. Shown, so a truncated ledger says it is truncated. */
  protected readonly pageSize = 200;

  protected readonly ledger = new LoadState<Page<Transaction>>('Could not load transactions.');
  protected readonly deleted = new LoadState<Transaction[]>('Could not load deleted transactions.');
  protected readonly accounts = signal<Account[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly saving = signal(false);
  protected readonly showDeleted = signal(false);
  /** Set when the date range cannot be used; the ledger is left as it was rather than blanked. */
  protected readonly rangeError = signal<string | null>(null);

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly rows = computed(() => this.ledger.value()?.content ?? []);
  protected readonly total = computed(() => this.ledger.value()?.totalElements ?? 0);
  protected readonly truncated = computed(() => this.total() > this.rows().length);

  protected readonly columns = ['date', 'description', 'account', 'category', 'amount', 'actions'];
  protected readonly deletedColumns = ['date', 'description', 'account', 'amount', 'restore'];

  protected readonly range = this.forms.nonNullable.group({
    from: [new Date(new Date().getFullYear(), new Date().getMonth(), 1) as Date | null],
    to: [new Date() as Date | null],
  });

  protected readonly form = this.forms.nonNullable.group({
    accountId: [0, Validators.required],
    transactionDate: [new Date() as Date | null, Validators.required],
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

    // A period to review, handed over from the reconciliation alert on the dashboard.
    const params = this.route.snapshot.queryParamMap;
    const from = parseIso(params.get('from'));
    const to = parseIso(params.get('to'));
    if (from && to) {
      this.range.setValue({ from, to }, { emitEvent: false });
    }

    // Debounced: a datepicker fires once per keystroke while typing a date, and each of those
    // used to be a request. Nothing inside reload() throws, which matters — an exception inside
    // this callback tears the subscription down for good, and that is exactly how clearing the
    // From field used to leave the spinner running until a page refresh.
    this.range.valueChanges.pipe(debounceTime(250)).subscribe(() => this.reload());
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
    const start = isoDate(from);
    const end = isoDate(to);
    if (start === null || end === null) {
      this.rangeError.set('Choose a real date at both ends of the range.');
      return;
    }
    if (start > end) {
      this.rangeError.set('The range ends before it starts.');
      return;
    }
    this.rangeError.set(null);
    this.ledger.run(this.api.transactions(start, end, 0, this.pageSize));
    if (this.showDeleted()) {
      this.deleted.run(this.api.deletedTransactions());
    }
  }

  protected toggleDeleted(): void {
    this.showDeleted.update((shown) => !shown);
    if (this.showDeleted()) {
      this.deleted.run(this.api.deletedTransactions());
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const date = isoDate(value.transactionDate);
    if (date === null) {
      this.snackBar.open('Choose a real date.', undefined, { duration: 3500 });
      return;
    }
    if (value.transfer && !value.transferAccountId) {
      this.snackBar.open('Choose the account the money moved to.', undefined, { duration: 3500 });
      return;
    }

    this.saving.set(true);
    this.api
      .createTransaction({
        accountId: value.accountId,
        transactionDate: date,
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
          // — it reads as though the save failed.
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
        error: (error: { status?: number }) => {
          this.saving.set(false);
          this.snackBar.open(
            error?.status === 409
              ? 'An identical transaction is already recorded for that day. If this is a second one, add something to the description that tells them apart.'
              : 'Could not save the transaction.',
            undefined,
            { duration: 6000 },
          );
        },
      });
  }

  protected recategorize(row: Transaction, categoryId: number | null): void {
    this.api.categorize(row.id, categoryId).subscribe({
      next: () => this.reload(),
      error: (error: { status?: number }) => {
        // 422 is the double-count rule: a transfer may never carry a category.
        this.snackBar.open(
          error?.status === 422
            ? 'Transfers are not categorized — the budget was charged when the purchase happened.'
            : 'Could not change the category.',
          undefined,
          { duration: 5000 },
        );
        // Reload on failure too. The select is bound one-way, so without this it kept showing
        // the category the server had just refused — the message said no, the row said yes.
        this.reload();
      },
    });
  }

  /**
   * Deletes, and offers to undo in the same breath.
   *
   * <p>Deletion is soft on the server and every leg of a transfer goes together, so this is
   * reversible — which is what makes an undo action honest rather than a promise. The snackbar
   * carries it; a blocking "are you sure?" would interrupt every delete to guard against the rare
   * one that was a mistake, when the mistake can simply be undone.
   */
  protected remove(row: Transaction): void {
    this.api.deleteTransaction(row.id).subscribe({
      next: () => {
        this.reload();
        const notice = this.snackBar.open(
          row.transferGroupId ? 'Transfer removed from both accounts' : 'Transaction removed',
          'Undo',
          { duration: 8000 },
        );
        notice.onAction().subscribe(() => this.restore(row));
      },
      error: () => this.snackBar.open('Could not remove it.', undefined, { duration: 4000 }),
    });
  }

  protected restore(row: Transaction): void {
    this.api.restoreTransaction(row.id).subscribe({
      next: (legs) => {
        this.reload();
        this.snackBar.open(
          legs.length > 1 ? 'Transfer restored on both accounts' : 'Transaction restored',
          undefined,
          { duration: 2500 },
        );
      },
      error: () => this.snackBar.open('Could not restore it.', undefined, { duration: 4000 }),
    });
  }
}

/** A yyyy-mm-dd query parameter as a local Date, or null for anything else. */
function parseIso(value: string | null): Date | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return Number.isFinite(date.getTime()) ? date : null;
}
