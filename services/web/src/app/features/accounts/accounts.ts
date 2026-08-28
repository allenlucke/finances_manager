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
import { ApiClient } from '../../core/api';
import { amountClass, money } from '../../core/money';
import { Account, LedgerEntity } from '../../core/models';

/** Accounts and their balances, plus the form to add one. */
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
  ],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class AccountsComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly entities = signal<LedgerEntity[]>([]);
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

  protected readonly form = this.forms.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    accountType: ['checking', Validators.required],
    ledgerEntityId: [0, Validators.required],
    // Last four only: docs/SECURITY.md is explicit that full account numbers are not stored
    // without a concrete reason.
    mask: ['', [Validators.pattern(/^\d{0,4}$/)]],
  });

  protected readonly total = computed(() =>
    this.accounts().reduce((sum, account) => sum + Number(account.balance), 0),
  );

  constructor() {
    this.api.entities().subscribe((entities) => {
      this.entities.set(entities);
      const personal = entities.find((e) => e.kind === 'personal') ?? entities[0];
      if (personal) {
        this.form.patchValue({ ledgerEntityId: personal.id });
      }
    });
    this.reload();
  }

  protected entityName(id: number): string {
    return this.entities().find((entity) => entity.id === id)?.name ?? '—';
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();

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
          this.form.patchValue({ name: '', mask: '' });
          this.form.markAsPristine();
          this.reload();
          this.snackBar.open('Account added', undefined, { duration: 2500 });
        },
        error: (error) => {
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
    this.api.accounts().subscribe((accounts) => this.accounts.set(accounts));
  }
}
