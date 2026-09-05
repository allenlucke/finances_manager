import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideZonelessChangeDetection } from '@angular/core';
import { LoginComponent } from './login';

/**
 * The sign-in screen is the one place a mistake locks the owner out of his own data, so the two
 * things that make that survivable are pinned here: a typo cannot become the passphrase, and a
 * lockout says so instead of blaming the credentials.
 */
describe('LoginComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<LoginComponent>>;
  let component: Record<string, any>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    httpMock = TestBed.inject(HttpTestingController);
    // The component asks whether setup is needed as soon as auth state resolves; this suite is
    // about the forms, so the answer is irrelevant as long as it does not go unmatched.
    httpMock.match(() => true).forEach((request) => request.flush({ required: true }));
  });

  afterEach(() => httpMock.verify({ ignoreCancelled: true }));

  it('refuses to create an account when the two passphrases disagree', () => {
    component['setupForm'].setValue({
      email: 'allen@feelingfroggy.llc',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: 'a-long-enough-passphrasf',
    });

    expect(component['setupForm'].invalid).toBe(true);

    component['submitSetup']();
    // Nothing was sent. A typo here would become the only way in.
    httpMock.expectNone('/api/v1/setup');
  });

  it('accepts the account when they agree', () => {
    component['setupForm'].setValue({
      email: 'allen@feelingfroggy.llc',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: 'a-long-enough-passphrase',
    });

    expect(component['setupForm'].valid).toBe(true);
  });

  it('still requires a passphrase long enough for the server to accept', () => {
    component['setupForm'].setValue({
      email: 'allen@feelingfroggy.llc',
      displayName: 'Allen',
      password: 'short',
      confirmPassword: 'short',
    });

    // Matching is not sufficient — two identical short passphrases are still rejected here rather
    // than by a 400 the person has to interpret.
    expect(component['setupForm'].invalid).toBe(true);
  });

  it('calls a lockout a lockout rather than blaming the passphrase', () => {
    component['loginForm'].setValue({
      username: 'allen@feelingfroggy.llc',
      password: 'a-long-enough-passphrase',
    });
    component['submitLogin']();

    httpMock.expectOne('/api/v1/auth/login').flush(null, { status: 429, statusText: 'Locked' });

    expect(component['error']()).toContain('Too many attempts');
    expect(component['busy']()).toBe(false);
  });

  it('a stale CSRF token is not blamed on the passphrase, and a fresh one is fetched', () => {
    component['loginForm'].setValue({
      username: 'allen@feelingfroggy.llc',
      password: 'a-long-enough-passphrase',
    });
    component['submitLogin']();

    httpMock.expectOne('/api/v1/auth/login').flush(null, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 't' });

    expect(component['error']()).toContain('stale');
    expect(component['error']()).not.toContain('not accepted');
    expect(component['busy']()).toBe(false);
  });

  it('blames the credentials when that is what went wrong', () => {
    component['loginForm'].setValue({
      username: 'allen@feelingfroggy.llc',
      password: 'wrong',
    });
    component['submitLogin']();

    httpMock
      .expectOne('/api/v1/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(component['error']()).toContain('not accepted');
  });

  it('hides the passphrase until asked, then shows it', () => {
    expect(component['revealed']()).toBe(false);
    component['toggleReveal']();
    expect(component['revealed']()).toBe(true);
  });
});
