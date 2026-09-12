import { HttpErrorResponse } from '@angular/common/http';
import { passkeyFailureMessage } from './passkeys';

/**
 * The browser rejects a ceremony with a DOMException, and the network fails with an
 * HttpErrorResponse, which is not an Error. Both used to reach the screen as either the W3C's own
 * sentence with a URL in it, or as "that passkey was not accepted" for a server that was down.
 */
describe('passkeyFailureMessage', () => {
  const fallback = 'That passkey was not accepted.';

  it('reads a dismissed or timed-out prompt as cancelled', () => {
    const message = passkeyFailureMessage(
      new DOMException('The operation …', 'NotAllowedError'),
      fallback,
    );
    expect(message).toContain('cancelled or timed out');
    expect(message).not.toContain('w3.org');
  });

  it('says when the browser or address cannot do passkeys at all', () => {
    expect(passkeyFailureMessage(new DOMException('x', 'SecurityError'), fallback)).toContain(
      'localhost or HTTPS',
    );
  });

  it('says the server could not be reached rather than blaming the passkey', () => {
    const down = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
    expect(passkeyFailureMessage(down, fallback)).toContain('Could not reach the server');

    const refused = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });
    expect(passkeyFailureMessage(refused, fallback)).toBe(fallback);
  });

  it('keeps a sentence this app wrote itself', () => {
    expect(passkeyFailureMessage(new Error('The server rejected the new passkey.'), fallback)).toBe(
      'The server rejected the new passkey.',
    );
    expect(passkeyFailureMessage('something else', fallback)).toBe(fallback);
  });
});
