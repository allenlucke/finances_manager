import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Auth } from './auth';
import { authInterceptor } from './auth-interceptor';

/**
 * The 401 fork is the most consequential branch in the app, and it contained a live bug: a failed
 * passkey assertion was treated as "no session" and logged the person out to the passphrase form.
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let calls: { signedOut: number; passkeyRequired: number };

  beforeEach(() => {
    calls = { signedOut: 0, passkeyRequired: 0 };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: Auth,
          useValue: {
            signedOut: () => calls.signedOut++,
            passkeyRequired: () => calls.passkeyRequired++,
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function fire(url: string, body: object | null) {
    http.get(url).subscribe({ error: () => undefined });
    backend.expectOne(url).flush(body, { status: 401, statusText: 'Unauthorized' });
  }

  it('treats a plain 401 on a data call as signed out', () => {
    fire('/api/v1/accounts', { error: 'unauthenticated' });
    expect(calls.signedOut).toBe(1);
  });

  it('routes factor_required to the passkey prompt, not the login form', () => {
    fire('/api/v1/accounts', { error: 'factor_required', factor: 'webauthn' });
    expect(calls.passkeyRequired).toBe(1);
    expect(calls.signedOut).toBe(0);
  });

  it('leaves a failed passkey assertion to the caller', () => {
    // Regression: this used to call signedOut(), flipping state to anonymous and swapping the
    // passkey prompt for a passphrase form the person had already satisfied.
    fire('/login/webauthn', null);
    fire('/webauthn/authenticate/options', null);
    expect(calls.signedOut).toBe(0);
    expect(calls.passkeyRequired).toBe(0);
  });

  it('leaves a failed password login to the login form', () => {
    fire('/api/v1/auth/login', null);
    expect(calls.signedOut).toBe(0);
  });
});
