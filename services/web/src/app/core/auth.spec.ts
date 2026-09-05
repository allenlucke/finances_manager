import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { Auth } from './auth';

describe('Auth', () => {
  let auth: Auth;
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        // Real routes for the URLs the tests navigate to; an empty table refuses them outright.
        provideRouter([
          { path: 'login', children: [] },
          { path: 'dashboard', children: [] },
        ]),
      ],
    });
    auth = TestBed.inject(Auth);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  it('a refresh that is refused for a missing passkey is not a sign-out', () => {
    // Observed in the browser suite: passkey accepted, then the refresh that follows was answered
    // factor_required for the other factor, and the app showed the passphrase form again.
    auth.refresh().subscribe();
    backend
      .expectOne('/api/v1/auth/me')
      .flush(
        { error: 'factor_required', factor: 'webauthn' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(auth.state()).toBe('passkey-required');
    expect(auth.user()).toBeNull();
  });

  it('a refresh with no session at all asks whether setup is needed', () => {
    auth.refresh().subscribe();
    backend
      .expectOne('/api/v1/auth/me')
      .flush({ error: 'unauthenticated' }, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/setup').flush({ required: false });

    expect(auth.state()).toBe('anonymous');
  });

  it('signs out locally even when the logout request fails', () => {
    // An expired CSRF token, an API restart, a dropped connection: the button used to do nothing
    // at all in every one of those cases and the person stayed "signed in" on screen.
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    auth.logout();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 403, statusText: 'Forbidden' });
    backend.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 't' });

    expect(auth.state()).toBe('anonymous');
    expect(auth.user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('signing out fetches a fresh CSRF token, so the next sign-in is not refused', () => {
    // Logout discards the CSRF cookie with the session. The token was only ever fetched at
    // bootstrap, so sign out then sign in — no reload between — was a 403 blamed on the passphrase.
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    auth.logout();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    const prime = backend.expectOne('/api/v1/auth/csrf');
    expect(prime.request.method).toBe('GET');
    prime.flush({ headerName: 'X-XSRF-TOKEN', token: 't' });
  });

  it('lands on the page that was asked for, and only inside this app', async () => {
    await router.navigateByUrl('/login?returnUrl=%2Ftransactions%3Ffrom%3D2026-08-01');
    expect(auth.landingUrl()).toBe('/transactions?from=2026-08-01');

    // Never a protocol-relative or absolute URL: that would be an open redirect.
    await router.navigateByUrl('/login?returnUrl=%2F%2Fevil.example');
    expect(auth.landingUrl()).toBe('/dashboard');
    await router.navigateByUrl('/login?returnUrl=https%3A%2F%2Fevil.example');
    expect(auth.landingUrl()).toBe('/dashboard');
    await router.navigateByUrl('/login');
    expect(auth.landingUrl()).toBe('/dashboard');
  });
});
