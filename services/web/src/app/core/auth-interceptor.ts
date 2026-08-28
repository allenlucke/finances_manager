import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Auth } from './auth';

/**
 * Turns the API's authentication answers into app state.
 *
 * <p>The API deliberately never redirects — a redirect would be followed transparently by `fetch`
 * and arrive as HTML with status 200, which the app could not detect. Instead it returns 401 with a
 * body that says which of two things is wrong, and this reads it:
 *
 * <ul>
 *   <li>`factor_required` — signed in, but a registered passkey has not been presented.
 *   <li>anything else — no session.
 * </ul>
 *
 * <p>Requests to the auth endpoints are passed through untouched: a failed login legitimately
 * returns 401, and reacting to it here would fight the login form for control of the UI.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(Auth);
  const isAuthCall =
    request.url.includes('/auth/login') ||
    request.url.includes('/auth/me') ||
    request.url.includes('/auth/csrf') ||
    request.url.includes('/setup');

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthCall) {
        if (error.error?.error === 'factor_required') {
          auth.passkeyRequired();
        } else {
          auth.signedOut();
        }
      }
      return throwError(() => error);
    }),
  );
};
