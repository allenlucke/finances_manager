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
      catchError(() => {
        this._user.set(null);
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

  logout() {
    this.api.logout().subscribe({
      next: () => {
        this._user.set(null);
        this._state.set('anonymous');
        this.router.navigate(['/login']);
      },
    });
  }
}
