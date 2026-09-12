import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Auth, AuthState } from './auth';
import { signedInGuard } from './signed-in-guard';

/**
 * The guard must not decide while auth state is unknown: on a hard refresh it runs before
 * `/auth/me` has answered, and deciding immediately bounced a signed-in person to the login
 * screen on every reload.
 */
describe('signedInGuard', () => {
  let state: ReturnType<typeof signal<AuthState>>;

  beforeEach(() => {
    state = signal<AuthState>('unknown');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: Auth, useValue: { state: state.asReadonly() } },
      ],
    });
  });

  function run(url: string): Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(() =>
      signedInGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
  }

  it('waits for auth state to settle rather than deciding on "unknown"', async () => {
    const decisions: unknown[] = [];
    run('/dashboard').subscribe((decision) => decisions.push(decision));
    TestBed.tick();
    expect(decisions).toEqual([]);

    state.set('signed-in');
    TestBed.tick();
    expect(decisions).toEqual([true]);
  });

  it('sends a signed-out person to sign in, carrying where they were headed', async () => {
    state.set('anonymous');
    const decision = await firstValueFrom(run('/transactions?from=2026-08-01'));

    expect(decision).toBeInstanceOf(UrlTree);
    const tree = decision as UrlTree;
    expect(tree.root.children['primary']?.segments.map((s) => s.path)).toEqual(['login']);
    expect(tree.queryParams['returnUrl']).toBe('/transactions?from=2026-08-01');
  });

  it('treats a passkey-required session as not yet signed in', async () => {
    state.set('passkey-required');
    const decision = await firstValueFrom(run('/dashboard'));

    expect(decision).toBeInstanceOf(UrlTree);
  });
});
