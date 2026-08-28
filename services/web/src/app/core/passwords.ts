/**
 * Asks the browser to remember a sign-in.
 *
 * <p>An SPA does not get this for free. Chrome offers to save a password when a real form submit
 * navigates the page — which never happens here, because the app posts with fetch and routes
 * client-side. The result is a login that silently never gets saved, and a person who has to
 * remember a passphrase they only typed once.
 *
 * <p>The Credential Management API is the explicit way to ask. Feature-detected, because Safari and
 * Firefox do not implement `PasswordCredential`; where it is missing this does nothing and the
 * browser's own heuristics are the fallback.
 */
export async function offerToSaveCredentials(
  email: string,
  password: string,
  displayName?: string,
): Promise<void> {
  const container = navigator.credentials as CredentialsContainer | undefined;
  const PasswordCredentialCtor = (
    window as unknown as {
      PasswordCredential?: new (data: {
        id: string;
        password: string;
        name?: string;
      }) => Credential;
    }
  ).PasswordCredential;

  if (!container?.store || !PasswordCredentialCtor) {
    return;
  }

  try {
    await container.store(
      new PasswordCredentialCtor({ id: email, password, name: displayName ?? email }),
    );
  } catch {
    // Declining the prompt, or a browser that refuses in this context. Never worth interrupting a
    // successful sign-in over.
  }
}
