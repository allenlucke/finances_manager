import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * Drives the two WebAuthn ceremonies against Spring Security's endpoints.
 *
 * <p>The wire format is fixed by the server, and the awkward part is that WebAuthn speaks
 * `ArrayBuffer` while JSON speaks strings: the server base64url-encodes every binary field, the
 * browser API demands real buffers, and the assertion has to be encoded again on the way back.
 * Every `decode`/`encode` below is one of those crossings — none is decoration.
 *
 * <p>Base64<b>url</b> specifically (`-`/`_`, no padding), not plain base64. Sending standard base64
 * fails with an opaque decode error on the server rather than anything that names the problem.
 *
 * <p>Angular's `HttpClient` adds the CSRF header automatically for same-origin writes, which is why
 * these are `HttpClient` calls rather than raw `fetch`.
 */
@Injectable({ providedIn: 'root' })
export class Passkeys {
  private readonly http = inject(HttpClient);

  /** True when this browser can do WebAuthn at all. */
  static supported(): boolean {
    return typeof window !== 'undefined' && !!window.PublicKeyCredential;
  }

  /**
   * Registers a new passkey for the signed-in user.
   *
   * <p>Requires an authenticated session: you enrol a passkey from inside the app, never from the
   * login screen. The label is what the management list shows, so it should say which device.
   */
  async register(label: string): Promise<void> {
    if (!label.trim()) {
      throw new Error('A passkey needs a label so you can tell your devices apart.');
    }

    const options = await firstValueFrom(
      this.http.post<PublicKeyCredentialCreationOptionsJSON>('/webauthn/register/options', {}),
    );

    const created = (await navigator.credentials.create({
      publicKey: {
        ...options,
        challenge: decode(options.challenge),
        user: { ...options.user, id: decode(options.user.id) },
        excludeCredentials: (options.excludeCredentials ?? []).map((credential) => ({
          ...credential,
          id: decode(credential.id),
        })),
      } as PublicKeyCredentialCreationOptions,
    })) as PublicKeyCredential | null;

    if (!created) {
      // The user dismissed the system prompt. Not an error worth shouting about.
      throw new Error('Passkey setup was cancelled.');
    }

    const response = created.response as AuthenticatorAttestationResponse;
    const result = await firstValueFrom(
      this.http.post<{ success: boolean }>('/webauthn/register', {
        publicKey: {
          credential: {
            id: created.id,
            rawId: encode(created.rawId),
            response: {
              attestationObject: encode(response.attestationObject),
              clientDataJSON: encode(response.clientDataJSON),
              // Tells the server whether this key is usb, internal, hybrid… which is what makes
              // the "will I lose this if I lose the device" answer possible later.
              transports: response.getTransports ? response.getTransports() : [],
            },
            type: created.type,
            clientExtensionResults: created.getClientExtensionResults(),
            authenticatorAttachment: created.authenticatorAttachment,
          },
          label,
        },
      }),
    );

    if (!result?.success) {
      throw new Error('The server rejected the new passkey.');
    }
  }

  /**
   * Presents an existing passkey.
   *
   * <p>Used for the second factor once one is registered. The server answers
   * `{authenticated, redirectUrl}`; the redirect is ignored deliberately — this is an SPA and the
   * router decides where to go, not the server.
   */
  async authenticate(): Promise<void> {
    const options = await firstValueFrom(
      this.http.post<PublicKeyCredentialRequestOptionsJSON>('/webauthn/authenticate/options', {}),
    );

    const asserted = (await navigator.credentials.get({
      publicKey: {
        ...options,
        challenge: decode(options.challenge),
        allowCredentials: (options.allowCredentials ?? []).map((credential) => ({
          ...credential,
          id: decode(credential.id),
        })),
      } as PublicKeyCredentialRequestOptions,
    })) as PublicKeyCredential | null;

    if (!asserted) {
      throw new Error('Passkey sign-in was cancelled.');
    }

    const response = asserted.response as AuthenticatorAssertionResponse;
    const result = await firstValueFrom(
      this.http.post<{ authenticated: boolean }>('/login/webauthn', {
        id: asserted.id,
        rawId: encode(asserted.rawId),
        response: {
          authenticatorData: encode(response.authenticatorData),
          clientDataJSON: encode(response.clientDataJSON),
          signature: encode(response.signature),
          userHandle: response.userHandle ? encode(response.userHandle) : undefined,
        },
        credType: asserted.type,
        clientExtensionResults: asserted.getClientExtensionResults(),
        authenticatorAttachment: asserted.authenticatorAttachment,
      }),
    );

    if (!result?.authenticated) {
      throw new Error('That passkey was not accepted.');
    }
  }

  list(): Promise<RegisteredPasskey[]> {
    return firstValueFrom(this.http.get<RegisteredPasskey[]>('/api/v1/passkeys'));
  }

  remove(credentialId: string): Promise<void> {
    // The id is base64url and may contain characters that are meaningful in a path.
    return firstValueFrom(
      this.http.delete<void>(`/api/v1/passkeys/${encodeURIComponent(credentialId)}`),
    );
  }
}

/**
 * What to tell the person when a ceremony fails.
 *
 * <p>`navigator.credentials.create` and `.get` reject with a `DOMException` rather than resolving
 * null, so `error.message` used to put "The operation either timed out or was not allowed. See:
 * https://www.w3.org/…" on the screen verbatim. And an `HttpErrorResponse` is not an `Error`, so a
 * server that was simply unreachable was reported as the passkey being refused.
 */
export function passkeyFailureMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401 || error.status === 403) {
      return fallback;
    }
    return 'Could not reach the server. Check the app is running and try again.';
  }
  if (error instanceof DOMException) {
    switch (error.name) {
      case 'NotAllowedError':
      case 'AbortError':
        return 'The passkey prompt was cancelled or timed out. Try again.';
      case 'InvalidStateError':
        return 'This device already has a passkey for this account.';
      case 'NotSupportedError':
      case 'SecurityError':
        return 'This browser or address cannot use passkeys. Passkeys need localhost or HTTPS.';
      default:
        return fallback;
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

export interface RegisteredPasskey {
  credentialId: string;
  label: string;
  created: string;
  lastUsed: string | null;
  /** Synced to a keychain, so losing the device does not lose the passkey. */
  backedUp: boolean;
}

/** Server JSON, before the binary fields are turned back into buffers. */
interface PublicKeyCredentialCreationOptionsJSON {
  challenge: string;
  user: { id: string; name: string; displayName: string };
  excludeCredentials?: { id: string; type: string; transports?: string[] }[];
  [key: string]: unknown;
}

interface PublicKeyCredentialRequestOptionsJSON {
  challenge: string;
  allowCredentials?: { id: string; type: string; transports?: string[] }[];
  [key: string]: unknown;
}

/** base64url → ArrayBuffer. */
function decode(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = window.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/** ArrayBuffer → base64url, unpadded. */
function encode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return window.btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
