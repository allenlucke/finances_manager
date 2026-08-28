import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { toObservable } from '@angular/core/rxjs-interop';
import { filter, map, take } from 'rxjs';
import { Auth } from './auth';

/**
 * Blocks a route until auth state is known, then allows or redirects.
 *
 * <p>Waiting for `unknown` to resolve matters on a hard refresh: the guard runs before
 * `/auth/me` has answered, and deciding immediately would bounce a signed-in user to the login
 * screen every time they reloaded the page.
 */
export const signedInGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);

  return toObservable(auth.state).pipe(
    filter((state) => state !== 'unknown'),
    take(1),
    map((state) => (state === 'signed-in' ? true : router.createUrlTree(['/login']))),
  );
};
