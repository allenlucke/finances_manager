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
import { signedOutGuard } from './signed-out-guard';

/** Keeps a signed-in person off the sign-in screen, and nobody else. */
describe('signedOutGuard', () => {
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

  function run(): Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(() =>
      signedOutGuard({} as ActivatedRouteSnapshot, { url: '/login' } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
  }

  it('sends a signed-in person to the dashboard', async () => {
    state.set('signed-in');
    const decision = await firstValueFrom(run());

    expect(decision).toBeInstanceOf(UrlTree);
    expect((decision as UrlTree).root.children['primary']?.segments.map((s) => s.path)).toEqual([
      'dashboard',
    ]);
  });

  it('lets everyone else through: signed out, setup pending, and a passkey still owed', async () => {
    for (const authState of ['anonymous', 'setup-required', 'passkey-required'] as AuthState[]) {
      state.set(authState);
      expect(await firstValueFrom(run()), authState).toBe(true);
    }
  });

  it('does not decide while auth state is unknown', () => {
    const decisions: unknown[] = [];
    run().subscribe((decision) => decisions.push(decision));
    TestBed.tick();

    expect(decisions).toEqual([]);
  });
});
