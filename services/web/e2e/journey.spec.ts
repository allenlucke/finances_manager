import { expect, Page, test } from '@playwright/test';
import { TEST_EMAIL, TEST_PASSPHRASE } from './reset-database';

/**
 * The whole product, through a browser: set up an account, sign in, create accounts and a category,
 * enter transactions including a transfer, and read the dashboard back.
 *
 * <p>This is the first test that renders anything. Everything above it verifies the API; this
 * verifies that a person can actually use the thing — cookies, CSRF, routing, guards and forms.
 */

// Shared with the reset guard on purpose: it decides what counts as the suite's own data, and if
// the two ever disagree the guard stops recognising the account this file creates.
const EMAIL = TEST_EMAIL;
const PASSPHRASE = TEST_PASSPHRASE;

/**
 * Signs in, creating the account first if the database is empty.
 *
 * <p>Waits for one of the two headings before deciding which form it is looking at. Checking too
 * early is not a hypothetical: while auth state is still `unknown` the shell renders only a
 * progress bar, so an immediate `isVisible()` returns false, the helper takes the sign-in branch,
 * and it then fills two of the three setup fields and clicks a button that isn't there.
 */
async function signIn(page: Page) {
  await page.goto('/');

  const setupHeading = page.getByText('Set up finances');
  const signInHeading = page.getByText('Sign in', { exact: true });
  await expect(setupHeading.or(signInHeading).first()).toBeVisible();

  if (await setupHeading.isVisible()) {
    await page.getByLabel('Email').fill(EMAIL);
    await page.getByLabel('Name').fill('Allen');
    // `exact` on both: getByLabel matches on substring, so a bare 'Passphrase' now resolves to
    // two fields and fails on strict mode rather than filling either.
    await page.getByLabel('Passphrase', { exact: true }).fill(PASSPHRASE);
    await page.getByLabel('Confirm passphrase', { exact: true }).fill(PASSPHRASE);
    await page.getByRole('button', { name: 'Create account' }).click();
  } else {
    await page.getByLabel('Email').fill(EMAIL);
    await page.getByLabel('Passphrase', { exact: true }).fill(PASSPHRASE);
    await page.getByRole('button', { name: 'Sign in' }).click();
  }

  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();
}

/**
 * Navigates and waits for the destination to actually render.
 *
 * <p>Clicking a link and immediately touching a field is a race, and Playwright's auto-waiting does
 * not save you from it: several screens have a "Name" field, so a locator resolves happily against
 * the page you are *leaving* and the fill lands there. Waiting on something unique to the
 * destination is what makes the navigation observable.
 */
async function goTo(page: Page, link: string, anchor: string) {
  await page.getByRole('link', { name: link }).click();
  await expect(page.getByText(anchor).first()).toBeVisible();
}

async function addAccount(page: Page, name: string, type: string) {
  await goTo(page, 'Accounts', 'Add an account');
  await page.getByLabel('Name').fill(name);
  await page.getByRole('combobox', { name: 'Type' }).click();
  await page.getByRole('option', { name: type, exact: true }).click();
  await page.getByRole('button', { name: 'Add account' }).click();
  await expect(page.getByRole('cell', { name, exact: true })).toBeVisible();
}

test.describe('Signed out', () => {
  test('a protected route redirects to login rather than flashing the app', async ({ page }) => {
    await page.goto('/dashboard');

    // The guard waits for auth state to resolve before deciding, so a hard refresh must not
    // briefly show a signed-in-looking shell.
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeHidden();
  });
});

test.describe('Deployed stack', () => {
  test('the passkey endpoints reach the API through nginx', async ({ request }) => {
    // These live outside /api and used to fall through to try_files: 405 with an HTML body,
    // and passkeys silently dead in the only stack that runs on the homelab. Anonymous callers
    // are refused (CSRF, 403) — the assertion is that the refusal came from the API as JSON,
    // not from nginx as a page.
    for (const path of ['/webauthn/authenticate/options', '/webauthn/register/options', '/login/webauthn']) {
      const response = await request.post(path, { data: {} });
      expect(response.status(), path).not.toBe(405);
      expect(response.headers()['content-type'] ?? '', path).toContain('json');
    }
  });

  test('an upload larger than a megabyte is not refused by nginx', async ({ request }) => {
    // nginx defaulted to 1 MB while the API allows 10 MB. Anonymous, so the API answers 403 —
    // the point is that it answered at all rather than nginx sending 413.
    const twoMegabytes = 'x'.repeat(2 * 1024 * 1024);
    const response = await request.post('/api/v1/imports', {
      multipart: { file: { name: 'big.csv', mimeType: 'text/csv', buffer: Buffer.from(twoMegabytes) } },
    });
    expect(response.status()).not.toBe(413);
  });
});

test.describe('Journey', () => {
  test('a person can set up, sign in, and reach the dashboard', async ({ page }) => {
    await signIn(page);

    await expect(page.getByText('Net worth')).toBeVisible();
  });

  test('a brand new account sees no broken dates anywhere on the dashboard', async ({ page }) => {
    await signIn(page);
    await expect(page.getByText('Net worth')).toBeVisible();

    // The literal first thing Allen saw on his own first sign-in. The spending card took its
    // month label from the first row of data, and with no data that was `monthLabel('')` —
    // which returns the *string* "Invalid Date", sailing straight through the `|| 'This month'`
    // fallback that was supposed to catch it.
    await expect(page.getByText('Invalid Date')).toHaveCount(0);
    await expect(page.locator('mat-card-subtitle')).toContainText([/\w+ \d{4}/]);
  });

  test('an account can be created and appears with a balance', async ({ page }) => {
    await signIn(page);

    await addAccount(page, 'Chase Sapphire', 'Credit card');

    // A brand new account reports zero rather than vanishing from the list.
    await expect(page.getByText('$0.00').first()).toBeVisible();
  });

  test('a purchase is recorded and shows as a negative amount', async ({ page }) => {
    await signIn(page);
    const account = 'Card';
    await addAccount(page, account, 'Credit card');

    await goTo(page, 'Budget', 'Add a category');
    await page.getByLabel('Name').fill('Groceries');
    await page.getByRole('button', { name: 'Add category' }).click();
    // The categories list — added because a created category previously vanished into a dropdown
    // with nowhere to see it.
    await expect(page.getByRole('cell', { name: 'Groceries' }).first()).toBeVisible();

    await goTo(page, 'Transactions', 'Add a transaction');
    // Role-scoped: getByLabel('Account') also matches the toolbar's "Account menu for ..." button.
    await page.getByRole('combobox', { name: 'Account' }).click();
    await page.getByRole('option', { name: account, exact: true }).click();
    await page.getByLabel('Amount').fill('84.31');
    await page.getByLabel('Description').fill('KROGER');
    await page.getByRole('button', { name: 'Add', exact: true }).click();

    // exact: the row's delete button is labelled "Remove KROGER", so a loose match hits two cells.
    await expect(page.getByRole('cell', { name: 'KROGER', exact: true })).toBeVisible();
    // Sign convention, visible to a person: money out reads as negative.
    await expect(page.getByText('-$84.31')).toBeVisible();
  });

  test('marking a transaction as a transfer swaps category for a destination account', async ({ page }) => {
    await signIn(page);
    await goTo(page, 'Transactions', 'Add a transaction');

    // Not cosmetic: it mirrors the server rule that a transfer is never categorized, so the user
    // cannot construct a request the API will reject with a 422.
    await expect(page.getByLabel('Category')).toBeVisible();

    await page.getByLabel('Transfer').check();

    await expect(page.getByLabel('Moved to')).toBeVisible();
    await expect(page.getByLabel('Category')).toBeHidden();
  });

  test('the import screen offers the review queue', async ({ page }) => {
    await signIn(page);
    await goTo(page, 'Import', 'Import a statement');

    await expect(page.getByText('Needs a category')).toBeVisible();
  });

  test('signing out returns to the login screen', async ({ page }) => {
    await signIn(page);

    await page.getByRole('button', { name: /Account menu/ }).click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeHidden();
  });
});
