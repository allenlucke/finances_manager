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
import { LoadState } from '../../core/load-state';
import {
  Account,
  Category,
  ImportResult,
  LedgerEntity,
  Page,
  Transaction,
} from '../../core/models';
import { amountClass, money } from '../../core/money';

/**
 * Statement import, positions import, and the queue of rows the importer could not categorize.
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

  // Behind LoadState like the rest of the screen. Bare `.subscribe()` calls with no error branch
  // left the account select empty and the create-accounts button disabled with no sentence
  // anywhere when a request failed — the state of an install with nothing in it, on a screen
  // whose whole job is putting something in.
  protected readonly accounts = new LoadState<Account[]>('Could not load accounts.');
  protected readonly entities = new LoadState<LedgerEntity[]>('Could not load the sets of books.');
  protected readonly categories = new LoadState<Category[]>('Could not load categories.');
  protected readonly accountList = computed(() => this.accounts.value() ?? []);
  protected readonly categoryList = computed(() => this.categories.value() ?? []);
  protected readonly referenceError = computed(
    () => this.accounts.error() ?? this.entities.error() ?? this.categories.error(),
  );
  protected readonly batches = new LoadState<ImportResult[]>('Could not load import history.');
  protected readonly reviewPage = new LoadState<Page<Transaction>>(
    'Could not load the review queue.',
  );
  protected readonly review = computed(() => this.reviewPage.value()?.content ?? []);
  protected readonly reviewTotal = computed(() => this.reviewPage.value()?.totalElements ?? 0);

  protected readonly linking = signal(false);
  protected readonly file = signal<File | null>(null);
  protected readonly positionsFile = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly lastResult = signal<ImportResult | null>(null);
  /**
   * Which upload produced `lastResult`. Both uploads write it, and both the result card and the
   * create-and-retry step used to guess from which file picker was empty — so with both pickers
   * populated, a positions upload that reported unlinked accounts was retried as the statement,
   * and a positions result read "N already present" for holdings that had been updated in place.
   */
  protected readonly lastKind = signal<'statement' | 'positions' | null>(null);

  /**
   * The file inputs, kept so they can be cleared after an upload. A file input fires `change`
   * only when its value changes, so after importing `cacu.csv` choosing `cacu.csv` again did
   * nothing at all: the signal stayed null and the button stayed disabled. Re-importing the same
   * file is a supported, common operation.
   */
  private statementInput: HTMLInputElement | null = null;
  private positionsInput: HTMLInputElement | null = null;

  protected readonly money = money;
  protected readonly amountClass = amountClass;

  protected readonly batchColumns = ['started', 'filename', 'status', 'applied', 'duplicates'];
  protected readonly reviewColumns = ['date', 'description', 'amount', 'category'];

  /**
   * Nullable, and not required: an export that names an account on every row needs none, and
   * the first import into an empty install has none to give. The control used to start at 0 with
   * `required`, which a number satisfies, so an empty install sent `accountId=0` and got a 400 it
   * could not read. Null is simply not sent, and a single-account file with no account chosen is
   * refused by the API with a sentence that now reaches the screen.
   */
  protected readonly form = this.forms.nonNullable.group({
    accountId: [null as number | null],
  });

  protected readonly canUpload = computed(() => !!this.file() && !this.busy());
  protected readonly canUploadPositions = computed(() => !!this.positionsFile() && !this.busy());

  /** The set of books new accounts land in: personal, which is what an import of yours means. */
  protected readonly defaultEntity = computed<LedgerEntity | undefined>(() => {
    const entities = this.entities.value() ?? [];
    return entities.find((entity) => entity.kind === 'personal') ?? entities[0];
  });

  constructor() {
    this.accounts.run(this.api.accounts(), (accounts) => {
      if (accounts.length && this.form.controls.accountId.value === null) {
        this.form.patchValue({ accountId: accounts[0].id });
      }
    });
    this.categories.run(this.api.categories());
    this.entities.run(this.api.entities());
    this.reload();
  }

  protected accountName(id: number | null): string {
    if (id === null) return '—';
    return this.accountList().find((account) => account.id === id)?.name ?? '—';
  }

  protected chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.statementInput = input;
    this.file.set(input.files?.[0] ?? null);
    this.lastResult.set(null);
  }

  protected choosePositions(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.positionsInput = input;
    this.positionsFile.set(input.files?.[0] ?? null);
    this.lastResult.set(null);
  }

  protected upload(): void {
    const chosen = this.file();
    if (!chosen || this.busy()) {
      return;
    }
    this.busy.set(true);

    this.api.importStatement(this.form.getRawValue().accountId, chosen).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.lastKind.set('statement');
        this.lastResult.set(result);
        // The file is kept when accounts still need creating, so the retry can reuse it.
        if (!result.unlinkedAccounts.length) {
          this.file.set(null);
          if (this.statementInput) this.statementInput.value = '';
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
      error: (error: { status?: number; error?: { detail?: string } }) => {
        this.busy.set(false);
        this.snackBar.open(failureMessage(error), undefined, { duration: 7000 });
        this.reload();
      },
    });
  }

  /** A positions export is a snapshot of holdings, not a statement; it never touches the ledger. */
  protected uploadPositions(): void {
    const chosen = this.positionsFile();
    if (!chosen || this.busy()) {
      return;
    }
    this.busy.set(true);

    this.api.importPositions(chosen).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.lastKind.set('positions');
        this.lastResult.set(result);
        if (!result.unlinkedAccounts.length) {
          this.positionsFile.set(null);
          if (this.positionsInput) this.positionsInput.value = '';
        }
        this.reload();
        this.snackBar.open(
          `Holdings recorded: ${result.appliedCount} new position(s), ${result.duplicateCount} updated.`,
          undefined,
          { duration: 6000 },
        );
      },
      error: (error: { status?: number; error?: { detail?: string } }) => {
        this.busy.set(false);
        this.snackBar.open(failureMessage(error), undefined, { duration: 7000 });
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
    const entity = this.defaultEntity();
    if (!result?.unlinkedAccounts.length || !entity || this.linking()) {
      return;
    }
    const positions = this.lastKind() === 'positions';

    this.linking.set(true);
    try {
      for (const unlinked of result.unlinkedAccounts) {
        await firstValueFrom(
          this.api.createAccount({
            name: unlinked.name?.trim() || `Account ending ${unlinked.mask ?? '????'}`,
            accountType: guessType(result.filename, positions),
            ledgerEntityId: entity.id,
            mask: unlinked.mask,
            // The link, so this import — and every later one — matches exactly.
            externalId: unlinked.key,
          }),
        );
      }
      this.accounts.run(this.api.accounts());

      if (positions) {
        this.uploadPositions();
      } else if (this.file()) {
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
      error: () => {
        this.snackBar.open('Could not set the category.', undefined, { duration: 4000 });
        this.reload();
      },
    });
  }

  private reload(): void {
    this.batches.run(this.api.imports());
    // The queue is only ever uncategorized, non-transfer rows: an uncategorized transfer is
    // correct, not pending.
    this.reviewPage.run(this.api.needsReview(0, 100));
  }
}

/** The best type for an account a file named. Brokerage exports name brokerage accounts. */
function guessType(filename: string | null, positions: boolean): string {
  const name = (filename ?? '').toLowerCase();
  if (positions || name.includes('accounts_history') || name.includes('portfolio')) {
    return 'brokerage';
  }
  return 'checking';
}

/**
 * What to tell the person. The API's own sentence when it wrote one; a plain one otherwise.
 *
 * <p>Exported for its spec. Two things this must not do: hide a reason the API wrote because it
 * arrived as a 400 rather than a 422 ("Unknown account" did, and the screen showed the fallback),
 * and claim "Nothing was saved" for a status it cannot interpret. A gateway timeout mid-import
 * arrives with no body at all, and by then the batch may well have committed — the history below
 * is the only honest answer to "did it?"
 */
export function failureMessage(error: {
  status?: number;
  error?: { detail?: string | null } | null;
}): string {
  const detail = error?.error?.detail?.trim();
  if (detail) {
    return detail;
  }
  if (error?.status === 422) {
    return 'That file could not be read. Check it is the export your institution produced.';
  }
  if (!error?.status || error.status >= 500) {
    return 'The server could not be reached, or did not answer. Check the import history below before trying again.';
  }
  return 'The import did not finish. Check the import history below before trying again.';
}
