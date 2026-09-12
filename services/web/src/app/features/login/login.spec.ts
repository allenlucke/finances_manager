import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideZonelessChangeDetection } from '@angular/core';
import { Auth } from '../../core/auth';
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

  /**
   * Renders the first-run setup form.
   *
   * <p>Which form the screen shows is the Auth service's state, not the component's, so a DOM
   * assertion has to put Auth into `setup-required` first: no session, and an API that says no
   * account exists yet.
   */
  function showSetupForm() {
    TestBed.inject(Auth).refresh().subscribe();
    httpMock
      .match('/api/v1/auth/me')
      .forEach((request) =>
        request.flush({ error: 'unauthenticated' }, { status: 401, statusText: 'Unauthorized' }),
      );
    httpMock.match('/api/v1/setup').forEach((request) => request.flush({ required: true }));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says the two passphrases disagree, instead of only greying out the button', () => {
    // The button being disabled is not an explanation. `mismatch` is a group-level error and
    // MatFormField renders its error slot from the *control*, so the message was never created:
    // a typo on the first screen of a fresh install said nothing at all, on an app with no
    // password reset. Asserting the form is invalid — which the test below does — passed happily
    // through all of that, so this one reads the DOM.
    const page = showSetupForm();
    component['setupForm'].setValue({
      email: 'owner@finances.invalid',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: 'a-long-enough-passphrasf',
    });
    fixture.detectChanges();

    expect(page.textContent).toContain("don't match");
    const submit = page.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it('stays quiet until the confirmation has something in it', () => {
    const page = showSetupForm();
    component['setupForm'].setValue({
      email: 'owner@finances.invalid',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: '',
    });
    fixture.detectChanges();

    expect(page.textContent).not.toContain("don't match");
  });

  it('the message goes once the two agree', () => {
    const page = showSetupForm();
    component['setupForm'].setValue({
      email: 'owner@finances.invalid',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: 'a-long-enough-passphrasf',
    });
    fixture.detectChanges();
    expect(page.textContent).toContain("don't match");

    component['setupForm'].controls.confirmPassword.setValue('a-long-enough-passphrase');
    fixture.detectChanges();

    expect(page.textContent).not.toContain("don't match");
    expect((page.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('the one screen a signed-out person can reach has a real heading', () => {
    // Every other screen got one in batch 3; this one was still rendering its title as a div, so
    // the signed-out app had nothing in its heading list.
    const page = showSetupForm();

    expect(page.querySelector('h1')?.textContent?.trim()).toBe('Set up finances');
  });

  it('refuses to create an account when the two passphrases disagree', () => {
    component['setupForm'].setValue({
      email: 'owner@finances.invalid',
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
      email: 'owner@finances.invalid',
      displayName: 'Allen',
      password: 'a-long-enough-passphrase',
      confirmPassword: 'a-long-enough-passphrase',
    });

    expect(component['setupForm'].valid).toBe(true);
  });

  it('still requires a passphrase long enough for the server to accept', () => {
    component['setupForm'].setValue({
      email: 'owner@finances.invalid',
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
      username: 'owner@finances.invalid',
      password: 'a-long-enough-passphrase',
    });
    component['submitLogin']();

    httpMock.expectOne('/api/v1/auth/login').flush(null, { status: 429, statusText: 'Locked' });

    expect(component['error']()).toContain('Too many attempts');
    expect(component['busy']()).toBe(false);
  });

  it('a stale CSRF token is not blamed on the passphrase, and a fresh one is fetched', () => {
    component['loginForm'].setValue({
      username: 'owner@finances.invalid',
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
      username: 'owner@finances.invalid',
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
