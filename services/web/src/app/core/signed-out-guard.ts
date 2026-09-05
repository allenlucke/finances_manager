import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { toObservable } from '@angular/core/rxjs-interop';
import { filter, map, take } from 'rxjs';
import { Auth } from './auth';

/**
 * Keeps a signed-in user off the login screen. Without this, `/login` rendered the sign-in form
 * underneath the signed-in toolbar, which reads as two contradictory states at once.
 */
export const signedOutGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);

  return toObservable(auth.state).pipe(
    filter((state) => state !== 'unknown'),
    take(1),
    map((state) => (state === 'signed-in' ? router.createUrlTree(['/dashboard']) : true)),
  );
};
