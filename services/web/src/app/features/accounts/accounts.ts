import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
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
import { amountClass, money } from '../../core/money';
import { Account, Holding, LedgerEntity, NetWorthRow } from '../../core/models';

/** Accounts and their balances, what each brokerage holds, and the form to add an account. */
@Component({
  selector: 'app-accounts',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class AccountsComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly accounts = new LoadState<Account[]>('Could not load accounts.');
  protected readonly entities = new LoadState<LedgerEntity[]>('Could not load the sets of books.');
  protected readonly netWorth = new LoadState<NetWorthRow[]>('Could not load net worth.');
  protected readonly holdings = new LoadState<Holding[]>('Could not load holdings.');
  protected readonly saving = signal(false);
  protected readonly money = money;
  protected readonly amountClass = amountClass;

  /**
   * Values mirror the `account_type` CHECK constraint in V2 exactly.
   *
   * <p>Lowercase on purpose: the same spelling crosses Postgres, Java, the Python service and this
   * client. Sending the Java constant name worked only by accident of Jackson's default binding.
   */
  protected readonly accountTypes = [
    { value: 'checking', label: 'Checking' },
    { value: 'savings', label: 'Savings' },
    { value: 'credit_card', label: 'Credit card' },
    { value: 'brokerage', label: 'Brokerage' },
    { value: 'loan', label: 'Loan' },
    { value: 'cash', label: 'Cash' },
  ];

  protected readonly columns = ['name', 'type', 'entity', 'mask', 'balance'];
  protected readonly holdingColumns = ['symbol', 'quantity', 'price', 'value', 'gain'];

  protected readonly form = this.forms.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    accountType: ['checking', Validators.required],
    // Null, not 0: `required` accepts a zero, and an id of 0 is a 400 the person cannot read.
    ledgerEntityId: [null as number | null, Validators.required],
    // Last four only: docs/SECURITY.md is explicit that full account numbers are not stored
    // without a concrete reason.
    mask: ['', [Validators.pattern(/^\d{0,4}$/)]],
  });

  /**
   * From the API, not summed here. The first version did `reduce((sum, a) => sum + Number(a.balance))`
   * in the browser — float arithmetic on money, forbidden by the model file's own comment, and a
   * second net worth that could disagree with the dashboard's. The server has one figure, computed
   * in NUMERIC(19,4); this shows it.
   */
  protected readonly combined = computed(
    () => this.netWorth.value()?.find((row) => row.ledgerEntityId === null)?.netWorth ?? null,
  );

  /** Holdings grouped by the account that holds them, in account order. */
  protected readonly holdingsByAccount = computed(() => {
    const byId = new Map<number, Holding[]>();
    for (const holding of this.holdings.value() ?? []) {
      byId.set(holding.accountId, [...(byId.get(holding.accountId) ?? []), holding]);
    }
    return (this.accounts.value() ?? [])
      .filter((account) => byId.has(account.id))
      .map((account) => ({
        account,
        asOf: byId.get(account.id)![0].asOf,
        rows: byId.get(account.id)!,
      }));
  });

  constructor() {
    this.entities.run(this.api.entities(), (entities) => {
      const personal = entities.find((e) => e.kind === 'personal') ?? entities[0];
      if (personal) {
        this.form.patchValue({ ledgerEntityId: personal.id });
      }
    });
    this.reload();
  }

  protected entityName(id: number): string {
    return this.entities.value()?.find((entity) => entity.id === id)?.name ?? '—';
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    if (value.ledgerEntityId === null) {
      return; // `required` already refuses this; the check narrows the type.
    }
    this.saving.set(true);

    this.api
      .createAccount({
        name: value.name,
        accountType: value.accountType,
        ledgerEntityId: value.ledgerEntityId,
        mask: value.mask || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          // reset, not patchValue: patching leaves the controls touched, so "required" flashes
          // red on a field the person just submitted successfully.
          this.form.controls.name.reset('');
          this.form.controls.mask.reset('');
          this.reload();
          this.snackBar.open('Account added', undefined, { duration: 2500 });
        },
        error: (error: { status?: number }) => {
          this.saving.set(false);
          // 409 is the entity-scoped unique name constraint, which is a real answer rather than
          // a bug: two accounts in the same entity may not share a name.
          this.snackBar.open(
            error?.status === 409
              ? 'An account with that name already exists for this entity.'
              : 'Could not add the account.',
            undefined,
            { duration: 4000 },
          );
        },
      });
  }

  private reload(): void {
    this.accounts.run(this.api.accounts());
    this.netWorth.run(this.api.netWorth());
    this.holdings.run(this.api.holdings());
  }
}
