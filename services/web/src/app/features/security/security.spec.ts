import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SecurityComponent } from './security';

/**
 * The security page states, in its own voice, how the account signs in. It must never say
 * "passphrase only" without having checked — not on a failed request, and not in the moment
 * before the answer lands. Every assertion here reads the DOM.
 */
describe('SecurityComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<SecurityComponent>>;

  const settle = () => new Promise((resolve) => setTimeout(resolve));
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(SecurityComponent);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // The notification card is a child; its one request is not this spec's concern.
    backend
      .match((r) => r.url.startsWith('/api/v1/digest'))
      .forEach((r) => r.flush(null, { status: 500, statusText: 'Error' }));
    backend.verify();
  });

  it('does not claim "passphrase only" before the list has arrived', () => {
    fixture.detectChanges();

    expect(text()).toContain('Checking');
    expect(text()).not.toContain('Passphrase only');
    backend.expectOne('/api/v1/passkeys').flush([]);
  });

  it('does not claim "passphrase only" when it could not check', async () => {
    backend.expectOne('/api/v1/passkeys').flush(null, { status: 500, statusText: 'Error' });
    await settle();
    fixture.detectChanges();

    expect(text()).toContain('Could not check which passkeys are registered');
    expect(text()).not.toContain('Passphrase only');
    expect(text()).not.toContain('No passkeys registered');
  });

  it('says "passphrase only" once it knows there are none', async () => {
    backend.expectOne('/api/v1/passkeys').flush([]);
    await settle();
    fixture.detectChanges();

    expect(text()).toContain('Passphrase only');
    expect(text()).toContain('No passkeys registered');
  });

  it('says both factors are required, and whether a passkey survives losing the device', async () => {
    backend.expectOne('/api/v1/passkeys').flush([
      {
        credentialId: 'abc',
        label: 'MacBook Touch ID',
        created: '2026-09-01T00:00:00Z',
        lastUsed: null,
        backedUp: false,
      },
    ]);
    await settle();
    fixture.detectChanges();

    expect(text()).toContain('Passphrase and a passkey are both required');
    // As text, not a tooltip: the answer to "will I lose this" must reach keyboard and
    // screen-reader users too.
    expect(text()).toContain('bound to this device');
  });

  it('a mismatched new passphrase is said in text, and nothing is sent', () => {
    backend.expectOne('/api/v1/passkeys').flush([]);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as Record<string, any>;
    component['passphraseForm'].setValue({
      current: 'the-current-passphrase',
      next: 'a-new-passphrase-that-is-long',
      confirm: 'a-different-one-entirely',
    });
    fixture.detectChanges();

    component['changePassphrase']();
    fixture.detectChanges();

    expect(text()).toContain('The two new passphrases do not match.');
    backend.expectNone('/api/v1/auth/password');
  });

  it('a refused change shows the server’s sentence; a good one clears it', () => {
    backend.expectOne('/api/v1/passkeys').flush([]);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as Record<string, any>;
    component['passphraseForm'].setValue({
      current: 'wrong-current-passphrase',
      next: 'a-new-passphrase-that-is-long',
      confirm: 'a-new-passphrase-that-is-long',
    });

    component['changePassphrase']();
    backend
      .expectOne('/api/v1/auth/password')
      .flush(
        { detail: 'The current passphrase was not accepted.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    fixture.detectChanges();
    expect(text()).toContain('The current passphrase was not accepted.');

    component['passphraseForm'].setValue({
      current: 'the-right-current-one',
      next: 'a-new-passphrase-that-is-long',
      confirm: 'a-new-passphrase-that-is-long',
    });
    component['changePassphrase']();
    const request = backend.expectOne('/api/v1/auth/password');
    expect(request.request.body).toEqual({
      currentPassword: 'the-right-current-one',
      newPassword: 'a-new-passphrase-that-is-long',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(text()).not.toContain('was not accepted');
  });
});
