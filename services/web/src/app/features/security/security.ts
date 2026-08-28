import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Passkeys, RegisteredPasskey } from '../../core/passkeys';

/**
 * Passkey management.
 *
 * <p>The consequence of adding or removing one is spelled out on the page rather than buried,
 * because it changes how the account authenticates: the first passkey silently promotes this
 * account to two factors, and removing the last one silently demotes it. Both are correct
 * behaviour and both are surprising if unannounced.
 */
@Component({
  selector: 'app-security',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class SecurityComponent {
  private readonly passkeys = inject(Passkeys);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly items = signal<RegisteredPasskey[]>([]);
  protected readonly busy = signal(false);
  protected readonly supported = Passkeys.supported();

  protected readonly columns = ['label', 'created', 'lastUsed', 'backedUp', 'actions'];

  /** With none registered the account is single-factor; the page says so plainly. */
  protected readonly twoFactor = computed(() => this.items().length > 0);

  protected readonly form = this.forms.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    this.reload();
  }

  protected async add(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    try {
      const first = this.items().length === 0;
      await this.passkeys.register(this.form.getRawValue().label.trim());
      this.form.reset({ label: '' });
      await this.reload();
      this.snackBar.open(
        first
          ? 'Passkey added. This account now requires it in addition to your passphrase.'
          : 'Passkey added.',
        undefined,
        { duration: 6000 },
      );
    } catch (error) {
      this.snackBar.open(message(error), undefined, { duration: 6000 });
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(passkey: RegisteredPasskey): Promise<void> {
    const last = this.items().length === 1;
    if (
      last &&
      !confirm(
        'This is your only passkey. Removing it returns the account to passphrase-only sign-in. Continue?',
      )
    ) {
      return;
    }

    this.busy.set(true);
    try {
      await this.passkeys.remove(passkey.credentialId);
      await this.reload();
      this.snackBar.open(
        last ? 'Passkey removed. This account is back to passphrase only.' : 'Passkey removed.',
        undefined,
        { duration: 6000 },
      );
    } catch {
      this.snackBar.open('Could not remove that passkey.', undefined, { duration: 4000 });
    } finally {
      this.busy.set(false);
    }
  }

  private async reload(): Promise<void> {
    try {
      this.items.set(await this.passkeys.list());
    } catch {
      this.items.set([]);
    }
  }
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : 'Could not add the passkey.';
}
