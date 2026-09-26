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
import { ApiClient } from '../../core/api';
import { Passkeys, RegisteredPasskey, passkeyFailureMessage } from '../../core/passkeys';
import { NotificationSettingsComponent } from './notification-settings';

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
    NotificationSettingsComponent,
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class SecurityComponent {
  private readonly passkeys = inject(Passkeys);
  private readonly api = inject(ApiClient);
  private readonly forms = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  /**
   * Null until the list has arrived. Three states, and the template branches on all three: a
   * failed request rendered "No passkeys registered" and "Passphrase only" — the security screen
   * asserting, in its own voice, that two-factor was off when it was on — and once that was fixed
   * the same claim was still made for the moment between opening the page and the answer landing.
   * A page that says "passphrase only" must have checked.
   */
  protected readonly items = signal<RegisteredPasskey[] | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly changingPassphrase = signal(false);
  /** Shown under the passphrase form: a mismatch, or the server's refusal. Cleared on success. */
  protected readonly passphraseError = signal<string | null>(null);
  protected readonly passphraseForm = this.forms.nonNullable.group({
    current: ['', Validators.required],
    next: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(200)]],
    confirm: ['', Validators.required],
  });

  protected changePassphrase(): void {
    if (this.passphraseForm.invalid || this.changingPassphrase()) return;
    const v = this.passphraseForm.getRawValue();
    if (v.next !== v.confirm) {
      this.passphraseError.set('The two new passphrases do not match.');
      return;
    }
    this.changingPassphrase.set(true);
    this.api.changePassword(v.current, v.next).subscribe({
      next: () => {
        this.changingPassphrase.set(false);
        this.passphraseError.set(null);
        this.passphraseForm.reset({ current: '', next: '', confirm: '' });
        this.snackBar.open('Passphrase changed. Let your browser save the new one.', undefined, {
          duration: 6000,
        });
      },
      error: (error: { error?: { detail?: string | null } | null }) => {
        this.changingPassphrase.set(false);
        this.passphraseError.set(error?.error?.detail || 'The passphrase could not be changed.');
      },
    });
  }
  protected readonly supported = Passkeys.supported();

  protected readonly columns = ['label', 'created', 'lastUsed', 'backedUp', 'actions'];

  protected readonly checking = computed(() => this.items() === null && this.loadError() === null);
  protected readonly registered = computed(() => this.items() ?? []);

  /** With none registered the account is single-factor; the page says so plainly — once it knows. */
  protected readonly twoFactor = computed(() => (this.items()?.length ?? 0) > 0);

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
      const first = this.registered().length === 0;
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
      this.snackBar.open(passkeyFailureMessage(error, 'Could not add the passkey.'), undefined, {
        duration: 6000,
      });
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(passkey: RegisteredPasskey): Promise<void> {
    const last = this.registered().length === 1;
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
    } catch (error) {
      this.snackBar.open(
        passkeyFailureMessage(error, 'Could not remove that passkey.'),
        undefined,
        {
          duration: 4000,
        },
      );
    } finally {
      this.busy.set(false);
    }
  }

  private async reload(): Promise<void> {
    try {
      this.items.set(await this.passkeys.list());
      this.loadError.set(null);
    } catch {
      this.items.set(null);
      this.loadError.set('Could not check which passkeys are registered.');
    }
  }
}
