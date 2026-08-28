import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiClient } from '../../core/api';
import {
  Account,
  ImportResult,
  LedgerEntity,
  Transaction,
  UnlinkedAccount,
} from '../../core/models';
import { amountClass, money } from '../../core/money';

/**
 * Statement import, and the queue of rows it could not categorize.
 *
 * <p>The result is reported as applied-versus-duplicate rather than a bare success, because the
 * interesting answer when you re-import an overlapping statement is "nothing changed" — and a
 * message that just says "imported" would leave you wondering whether it doubled everything.
 */
@Component({
  selector: 'app-import',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './import.html',
  styleUrl: './import.scss',
})
export class ImportComponent {
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly batches = signal<ImportResult[]>([]);
  protected readonly review = signal<Transaction[]>([]);
  protected readonly categories = signal<{ id: number; name: string }[]>([]);
  protected readonly entities = signal<LedgerEntity[]>([]);
  protected readonly linking = signal(false);
  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly lastResult = signal<ImportResult | null>(null);

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly batchColumns = ['started', 'filename', 'status', 'applied', 'duplicates'];
  protected readonly reviewColumns = ['date', 'description', 'amount', 'category'];

  protected readonly form = this.forms.nonNullable.group({
    accountId: [0, Validators.required],
  });

  protected readonly canUpload = computed(() => !!this.file() && !this.busy());

  constructor() {
    this.api.accounts().subscribe((accounts) => {
      this.accounts.set(accounts);
      if (accounts.length) {
        this.form.patchValue({ accountId: accounts[0].id });
      }
    });
    this.api
      .categories()
      .subscribe((categories) =>
        this.categories.set(categories.map((c) => ({ id: c.id, name: c.name }))),
      );
    this.api.entities().subscribe((entities) => this.entities.set(entities));
    this.reload();
  }

  protected accountName(id: number | null): string {
    if (id === null) return '—';
    return this.accounts().find((account) => account.id === id)?.name ?? '—';
  }

  protected chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.lastResult.set(null);
  }

  protected upload(): void {
    const chosen = this.file();
    if (!chosen || this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);

    this.api.importStatement(this.form.getRawValue().accountId, chosen).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.lastResult.set(result);
        // The file is kept when accounts still need creating, so the retry can reuse it.
        if (!result.unlinkedAccounts?.length) {
          this.file.set(null);
        }
        this.reload();
        this.snackBar.open(
          result.appliedCount === 0
            ? `Nothing new — all ${result.duplicateCount} rows were already here.`
            : `Added ${result.appliedCount} transaction(s), skipped ${result.duplicateCount} duplicate(s).`,
          undefined,
          { duration: 6000 },
        );
      },
      error: (error) => {
        this.busy.set(false);
        this.snackBar.open(
          error?.status === 422
            ? 'That file could not be parsed. Check it is the CSV your bank exported.'
            : 'The import failed.',
          undefined,
          { duration: 6000 },
        );
        this.reload();
      },
    });
  }

  /**
   * Creates the accounts a file named but this system does not have, then imports again.
   *
   * <p>The file already carries the account's name and a stable link, so there is nothing for a
   * person to look up or type. Requiring someone to know their own last four digits — and to know
   * that typing them is what makes an import work — was the wrong design.
   */
  protected async createAndRetry(): Promise<void> {
    const result = this.lastResult();
    const chosen = this.file();
    const entity = this.entities()[0];
    if (!result?.unlinkedAccounts.length || !entity || this.linking()) {
      return;
    }

    this.linking.set(true);
    try {
      for (const unlinked of result.unlinkedAccounts) {
        await firstValueFrom(
          this.api.createAccount({
            name: unlinked.name?.trim() || `Account ending ${unlinked.mask ?? '????'}`,
            // Type is a guess the user can correct; getting the rows in matters more than the label.
            accountType: 'checking',
            ledgerEntityId: entity.id,
            mask: unlinked.mask,
            // The link, so this import — and every later one — matches exactly.
            externalId: unlinked.key,
          }),
        );
      }
      this.api.accounts().subscribe((accounts) => this.accounts.set(accounts));

      if (chosen) {
        // Safe to repeat: rows already present come back as duplicates.
        this.upload();
      } else {
        this.snackBar.open('Accounts created. Choose the file again to import it.', undefined, {
          duration: 5000,
        });
      }
    } catch {
      this.snackBar.open('Could not create those accounts.', undefined, { duration: 4000 });
    } finally {
      this.linking.set(false);
    }
  }

  protected categorize(row: Transaction, categoryId: number | null): void {
    this.api.categorize(row.id, categoryId).subscribe({
      next: () => this.reload(),
      error: () => this.snackBar.open('Could not set the category.', undefined, { duration: 4000 }),
    });
  }

  private reload(): void {
    this.api.imports().subscribe((batches) => this.batches.set(batches));
    // The queue is only ever uncategorized, non-transfer rows: an uncategorized transfer is
    // correct, not pending.
    this.api.needsReview(0, 100).subscribe((page) => this.review.set(page.content));
  }
}
