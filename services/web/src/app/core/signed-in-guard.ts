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
export const signedInGuard: CanActivateFn = (_route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);

  return toObservable(auth.state).pipe(
    filter((authState) => authState !== 'unknown'),
    take(1),
    map((authState) =>
      authState === 'signed-in'
        ? true
        : // Carry the destination through, so signing in lands where the person was headed
          // rather than always on the dashboard.
          router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
    ),
  );
};
