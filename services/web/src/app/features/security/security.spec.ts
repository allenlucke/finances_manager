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

  afterEach(() => backend.verify());

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
});
