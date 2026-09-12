import { Component, inject, signal } from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroupDirective,
  NgForm,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiClient } from '../../core/api';
import { Auth } from '../../core/auth';
import { Passkeys, passkeyFailureMessage } from '../../core/passkeys';
import { offerToSaveCredentials } from '../../core/passwords';

/**
 * Sign in, or create the first account on a fresh install.
 *
 * <p>Which of the two it shows is decided by the API (`GET /api/v1/setup`), not by anything the user
 * clicks — a fresh database has no account to sign in with, and asking for credentials that cannot
 * exist is a dead end.
 */
@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  private readonly api = inject(ApiClient);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly forms = inject(FormBuilder);
  private readonly passkeys = inject(Passkeys);

  protected readonly state = this.auth.state;
  protected readonly passkeysSupported = Passkeys.supported();
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Lets someone check what they typed, rather than trusting a row of dots. */
  protected readonly revealed = signal(false);
  protected readonly confirmMatcher = new ConfirmPassphraseErrorMatcher();

  protected readonly loginForm = this.forms.nonNullable.group({
    username: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly setupForm = this.forms.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email]],
      displayName: ['', Validators.required],
      // Matches the server's rule. Length beats character classes, and this account is reachable
      // only over Tailscale (D-16).
      password: ['', [Validators.required, Validators.minLength(12)]],
      // A confirmation field earns its place twice over: it catches a typo in a passphrase that is
      // about to become the only way in, and its presence is what makes password managers
      // recognise this as an account-creation form worth offering to save.
      confirmPassword: ['', Validators.required],
    },
    { validators: passphrasesMatch },
  );

  protected toggleReveal(): void {
    this.revealed.update((shown) => !shown);
  }

  protected submitLogin(): void {
    if (this.loginForm.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { username, password } = this.loginForm.getRawValue();
    this.api.login(username, password).subscribe({
      next: () => {
        // Ask the browser to remember it. Without this an SPA login is never offered for saving,
        // because no form submit ever navigates.
        void offerToSaveCredentials(username, password);
        // Navigate only once auth state has actually settled. Routing first lets the guard read
        // the pre-login state and redirect straight back here.
        this.auth.refresh().subscribe(() => {
          this.busy.set(false);
          this.router.navigateByUrl(this.auth.landingUrl());
        });
      },
      error: (failure) => {
        this.busy.set(false);
        if (failure?.status === 403) {
          // Not the credentials: the CSRF token is stale or gone (a sign-out, a long idle tab).
          // Blaming the passphrase here sends someone to retype the one thing that was right.
          // Fetch a fresh token so the retry can succeed, and say what actually happened.
          this.api.primeCsrf().subscribe({ error: () => undefined });
          this.error.set('The page had gone stale. Try signing in again.');
          return;
        }
        this.error.set(signInFailureMessage(failure?.status));
      },
    });
  }

  /** Signs out of the half-finished session, for someone whose passkey is not to hand. */
  protected abandon(): void {
    this.error.set(null);
    this.auth.logout();
  }

  /**
   * Completes the second factor for a session that already passed the passphrase.
   *
   * <p>The server answered 401 with `factor_required`, which is why this screen is showing. On
   * success the session gains the WebAuthn factor and the same requests start succeeding — no
   * re-login, because the first factor was never in doubt.
   */
  protected async presentPasskey(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.passkeys.authenticate();
      await firstValueFrom(this.auth.refresh());
      this.router.navigateByUrl(this.auth.landingUrl());
    } catch (error) {
      this.error.set(passkeyFailureMessage(error, 'That passkey was not accepted.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected submitSetup(): void {
    if (this.setupForm.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { email, displayName, password } = this.setupForm.getRawValue();
    this.api.createFirstUser(email, displayName, password).subscribe({
      next: () => {
        // Straight into a session rather than making them retype what they just chose.
        this.api.login(email, password).subscribe({
          next: () => {
            void offerToSaveCredentials(email, password, displayName);
            this.auth.refresh().subscribe(() => {
              this.busy.set(false);
              this.router.navigateByUrl(this.auth.landingUrl());
            });
          },
          error: () => {
            this.busy.set(false);
            this.error.set('Account created, but signing in failed. Try signing in.');
          },
        });
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Could not create the account. It may already exist.');
      },
    });
  }
}

/** Cross-field check: the two passphrase boxes must agree. */
/**
 * Puts the Confirm passphrase field into its error state when the *group* reports a mismatch.
 *
 * <p>Without this the message never reached the screen at all. `mismatch` is a group-level error —
 * it has to be, since it compares two controls — but `MatFormField` renders its error slot only
 * when the control itself is in an error state, and a non-empty confirm field is perfectly valid on
 * its own. The projected `<mat-error>` was therefore never created: a typo greyed out Create
 * account and explained nothing, on the first screen of a fresh install, for an app whose own copy
 * says there is no password reset. Disabled buttons are not focusable either, so there was nothing
 * to inspect. Found by the QA pass on 2026-09-06; `login.spec.ts` now asserts the text is in the
 * DOM rather than merely that the form is invalid, which is what let this pass for so long.
 */
class ConfirmPassphraseErrorMatcher implements ErrorStateMatcher {
  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    if (control?.invalid && control.touched) {
      return true;
    }
    // Only once something has been typed: an empty field is not yet a mistake.
    return !!control?.value && !!form?.hasError('mismatch');
  }
}

/**
 * What a failed sign-in means, by status. Exported for its spec.
 *
 * <p>"Not accepted" is said only when the server actually refused the credentials. A lockout is
 * called a lockout: the usual reason to hide it — not confirming an address exists — buys nothing
 * on a single-user app reachable only from this machine, while "not accepted" in the face of a
 * correct passphrase is genuinely maddening. And a server that could not be reached at all — a
 * status of 0, a 502 while the API restarts — used to be reported as the passphrase being wrong,
 * which sent someone to retype the one thing that was right.
 */
export function signInFailureMessage(status: number | undefined): string {
  if (status === 401) {
    return 'That email and passphrase combination was not accepted.';
  }
  if (status === 429) {
    return 'Too many attempts. Wait a few minutes and try again.';
  }
  if (!status || status >= 500) {
    return 'Could not reach the server. Check the app is running and try again.';
  }
  return `Sign-in failed (the server answered ${status}). Try again.`;
}

function passphrasesMatch(group: import('@angular/forms').AbstractControl) {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return !confirm || password === confirm ? null : { mismatch: true };
}
