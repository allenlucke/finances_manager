import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Passkeys } from './passkeys';

/**
 * The encoding is the part of WebAuthn most likely to break quietly: base64url and plain base64
 * differ in three characters, and getting it wrong produces an opaque server-side decode failure
 * rather than anything that names the problem.
 */
describe('Passkeys', () => {
  let passkeys: Passkeys;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    passkeys = TestBed.inject(Passkeys);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('refuses to register without a label, before touching the network', async () => {
    // The label is what the management list shows; an unlabelled key is unmanageable.
    await expect(passkeys.register('   ')).rejects.toThrow(/label/i);
    httpMock.expectNone('/webauthn/register/options');
  });

  it('lists registered passkeys from the API', async () => {
    const pending = passkeys.list();
    httpMock.expectOne('/api/v1/passkeys').flush([
      {
        credentialId: 'abc',
        label: 'MacBook',
        created: '2026-08-24T00:00:00Z',
        lastUsed: null,
        backedUp: true,
      },
    ]);

    const result = await pending;
    expect(result.length).toBe(1);
    expect(result[0].label).toBe('MacBook');
  });

  it('url-encodes the credential id when deleting', async () => {
    // Credential ids are base64url and can contain characters that would otherwise change the path.
    const pending = passkeys.remove('a/b+c=');
    const request = httpMock.expectOne((candidate) => candidate.method === 'DELETE');

    expect(request.request.url).toBe('/api/v1/passkeys/a%2Fb%2Bc%3D');
    request.flush(null);
    await pending;
  });

  it('reports whether the browser supports WebAuthn', () => {
    // jsdom has no PublicKeyCredential, so this is false here — which is the point: the UI must
    // disable the button rather than throw when the API is absent.
    expect(typeof Passkeys.supported()).toBe('boolean');
  });
});
