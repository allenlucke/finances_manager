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

  it('signs out locally even when the logout request fails', () => {
    // An expired CSRF token, an API restart, a dropped connection: the button used to do nothing
    // at all in every one of those cases and the person stayed "signed in" on screen.
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    auth.logout();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(auth.state()).toBe('anonymous');
    expect(auth.user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
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
