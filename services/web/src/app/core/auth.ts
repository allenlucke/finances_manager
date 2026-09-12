import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { ApiClient } from './api';
import { Me } from './models';

/** What the API is currently asking of us. */
export type AuthState =
  'unknown' | 'anonymous' | 'setup-required' | 'passkey-required' | 'signed-in';

/**
 * Holds who is signed in, and what the API last said it needed.
 *
 * <p>`passkey-required` is a distinct state rather than a variant of anonymous. The API answers 401
 * with `{"error":"factor_required"}` when a passkey is registered but has not been presented, and
 * sending that user back to a password form they already satisfied is a dead end — it is the kind
 * of thing that makes people turn MFA off.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly api = inject(ApiClient);
  private readonly router = inject(Router);

  private readonly _user = signal<Me | null>(null);
  private readonly _state = signal<AuthState>('unknown');

  readonly user = this._user.asReadonly();
  readonly state = this._state.asReadonly();
  readonly signedIn = computed(() => this._state() === 'signed-in');

  /**
   * Called once at startup. Primes CSRF, then asks who we are.
   * A 401 here is expected and not an error — it just means "show the login screen".
   */
  bootstrap() {
    this.api.primeCsrf().subscribe({
      next: () => this.refresh().subscribe(),
      // Even the CSRF call failing should not leave the app stuck on a spinner.
      error: () => this._state.set('anonymous'),
    });
  }

  /**
   * Re-reads who is signed in.
   *
   * <p>Returns an observable that completes only once {@link state} has settled, and callers must
   * wait for it before navigating. Firing this and routing immediately is a real bug, not a
   * tidiness point: the route guard passes any state that is not `unknown`, so it would read the
   * *previous* state — `setup-required` or `anonymous` — and bounce the user straight back to the
   * login screen they just successfully came through.
   */
  refresh(): Observable<Me | null> {
    return this.api.me().pipe(
      tap((me) => {
        this._user.set(me);
        this._state.set('signed-in');
      }),
      catchError((error: unknown) => {
        this._user.set(null);
        // A session that passed the passphrase but still owes a passkey is not "signed out". The
        // interceptor deliberately leaves this call alone (it is an auth call), so the answer has
        // to be read here — otherwise a factor-required refresh flipped the state to anonymous and
        // put the passphrase form back in front of someone who had just satisfied it.
        const factor = factorRequired(error);
        if (factor === 'webauthn') {
          this._state.set('passkey-required');
          return of(null);
        }
        if (factor !== null) {
          // The server wants a factor this app cannot present here — a password on a session
          // that only ever showed a passkey. Not a passkey step, then: a sign-in. Treating every
          // factor_required as passkey-required looped /login and /dashboard with nothing to do.
          this._state.set('anonymous');
          return of(null);
        }
        // Distinguish "no account exists yet" from "not signed in", so a fresh install lands on
        // setup instead of a login form nobody can satisfy. Folded into the same stream so the
        // caller's completion still means "state is settled".
        return this.api.setupRequired().pipe(
          tap((result) => this._state.set(result.required ? 'setup-required' : 'anonymous')),
          catchError(() => {
            this._state.set('anonymous');
            return of(null);
          }),
          map(() => null),
        );
      }),
    );
  }

  /** Called by the interceptor when the API reports a missing second factor. */
  passkeyRequired() {
    this._state.set('passkey-required');
    this.router.navigate(['/login']);
  }

  /** Called by the interceptor on a plain 401. */
  signedOut() {
    this._user.set(null);
    if (this._state() !== 'setup-required') {
      this._state.set('anonymous');
    }
    this.router.navigate(['/login']);
  }

  /**
   * Signs out locally whatever the server says.
   *
   * <p>The logout request can fail — an expired CSRF token after a long session, an API restart,
   * a dropped connection — and the button used to do nothing at all in that case: no navigation,
   * no message, still "signed in" on screen. Clearing local state unconditionally means the person
   * is out of the app either way; the server session, if it survived, expires on its own.
   */
  logout() {
    const done = () => {
      this._user.set(null);
      this._state.set('anonymous');
      this.router.navigate(['/login']);
      // Logging out discards the CSRF cookie along with the session, and the token is only ever
      // fetched at bootstrap. Without this the very next sign-in — no reload in between — was
      // refused with a 403 that the form reported as a wrong passphrase. The browser suite's
      // passkey journey, which signs out and straight back in, is what found it.
      this.api.primeCsrf().subscribe({ error: () => undefined });
    };
    this.api.logout().subscribe({ next: done, error: done });
  }

  /** Where to go after a successful sign-in: the page that bounced us here, or the dashboard. */
  landingUrl(): string {
    const requested = this.router.parseUrl(this.router.url).queryParams['returnUrl'];
    // Only a path within this app. A full URL here would be an open redirect.
    return typeof requested === 'string' && requested.startsWith('/') && !requested.startsWith('//')
      ? requested
      : '/dashboard';
  }
}

/** Which factor the API asked for, or null when the 401 was not about a factor at all. */
function factorRequired(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
    return null;
  }
  const body = error.error as { error?: string; factor?: string } | null;
  if (body?.error !== 'factor_required') {
    return null;
  }
  // The server has always named the factor; an unnamed one is read as the passkey, which is the
  // only second factor this app knows how to present.
  return body.factor ?? 'webauthn';
}
