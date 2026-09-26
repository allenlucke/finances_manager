import { expect, test } from '@playwright/test';
import { EMAIL, PASSPHRASE, goTo, signIn, signOut } from './helpers';

/**
 * Passkeys, end to end, against a virtual authenticator.
 *
 * <p>Review finding F8: two real passkey bugs (B2, B3) shipped because nothing automated ever
 * ran the ceremony. Chromium's WebAuthn virtual authenticator, driven over CDP, performs a
 * genuine registration and assertion — the API verifies real signatures against the e2e stack's
 * own origin, which is why infra/e2e.env has to list port 4201.
 *
 * <p>Registering a passkey promotes the account to two-factor immediately, so this file ends by
 * removing it again: whatever runs after must find a passphrase-only account. The teardown's
 * TRUNCATE covers the case where this test dies halfway.
 */
test.describe('Passkeys', () => {
  test('a passkey can be added, is then required at sign-in, and satisfies the second factor', async ({
    page,
  }) => {
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('WebAuthn.enable');
    const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
      options: {
        protocol: 'ctap2',
        transport: 'internal',
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
        automaticPresenceSimulation: true,
      },
    });

    try {
      await signIn(page);
      await openSecurity(page);

      await page.getByLabel('Device name').fill('Virtual authenticator');
      await page.getByRole('button', { name: 'Add passkey' }).click();

      // The first passkey makes the second factor required at once, so the very next API call
      // from this password-only session may be answered with "present your passkey". Either
      // outcome is correct; what is not acceptable is an error.
      const listed = page.getByRole('cell', { name: 'Virtual authenticator', exact: true });
      const challenge = page.getByRole('button', { name: 'Use passkey' });
      await expect(listed.or(challenge).first()).toBeVisible();
      if (await challenge.isVisible()) {
        await challenge.click();
        await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();
        await openSecurity(page);
      }
      await expect(listed).toBeVisible();
      await expect(page.getByText('Passphrase and a passkey are both required')).toBeVisible();

      // Sign out and back in: the passphrase alone must no longer be enough.
      await signOut(page);
      await page.getByLabel('Email').fill(EMAIL);
      await page.getByLabel('Passphrase', { exact: true }).fill(PASSPHRASE);
      await page.getByRole('button', { name: 'Sign in' }).click();

      await expect(page.getByText('Passkey required')).toBeVisible();
      await expect(page.getByRole('link', { name: 'Dashboard' })).toBeHidden();

      await page.getByRole('button', { name: 'Use passkey' }).click();
      await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();

      // Back to passphrase-only, so the rest of the suite is not locked behind this authenticator.
      await openSecurity(page);
      page.once('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Remove Virtual authenticator' }).click();
      await expect(page.getByText('No passkeys registered.')).toBeVisible();
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId });
    }
  });
});

/** The security page lives in the account menu, not the top navigation. */
async function openSecurity(page: import('@playwright/test').Page): Promise<void> {
  await page.getByRole('button', { name: /Account menu/ }).click();
  await page.getByRole('menuitem', { name: 'Sign-in security' }).click();
  await expect(page.getByRole('heading', { name: 'Sign-in security' })).toBeVisible();
}
