import { expect, test } from '@playwright/test';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { addAccount, goTo, signIn, signOut } from './helpers';

/**
 * The whole product, through a browser: set up an account, sign in, create accounts and a category,
 * enter transactions including a transfer, upload a statement, and read the dashboard back.
 *
 * <p>This is the first test that renders anything. Everything above it verifies the API; this
 * verifies that a person can actually use the thing — cookies, CSRF, routing, guards and forms.
 */

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
    await goTo(page, 'Import', 'Needs a category');

    await expect(page.getByText('Needs a category')).toBeVisible();
  });

  test('a statement can be uploaded, lands in the ledger, and a second upload adds nothing', async ({ page }) => {
    // The product's primary workflow, and until now the one thing no automated test uploaded.
    // This goes through the real parser in the e2e stack's own AI container, not a stub.
    await signIn(page);
    await addAccount(page, 'Everyday Checking', 'Checking');

    await goTo(page, 'Import', 'Needs a category');
    await page.getByRole('combobox', { name: 'Account' }).click();
    await page.getByRole('option', { name: 'Everyday Checking', exact: true }).click();
    const fixture = statementDatedThisMonth();
    await page.getByLabel('Statement file').setInputFiles(fixture);
    await page.getByRole('button', { name: 'Import', exact: true }).click();

    await expect(page.getByText('Added 2 transaction(s), skipped 0 duplicate(s).')).toBeVisible();
    // The review queue shows what landed: neither row matches a merchant rule, so both wait.
    await expect(page.getByRole('cell', { name: 'COFFEE SHOP', exact: true })).toBeVisible();

    // Safe to repeat: the same file again is a no-op, not a doubling.
    await page.getByLabel('Statement file').setInputFiles(fixture);
    await page.getByRole('button', { name: 'Import', exact: true }).click();
    await expect(page.getByText('Nothing new — all 2 rows were already here.')).toBeVisible();

    await goTo(page, 'Transactions', 'Add a transaction');
    await expect(page.getByRole('cell', { name: 'PAYCHECK', exact: true })).toHaveCount(1);
    await expect(page.getByText('$2,500.00')).toBeVisible();
  });

  test('signing out returns to the login screen', async ({ page }) => {
    await signIn(page);

    await signOut(page);

    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeHidden();
  });
});


/**
 * A two-row generic export, written fresh with this month's dates.
 *
 * <p>Not a checked-in fixture: the ledger opens on the current month, so a file with fixed dates
 * imports fine and then shows nothing on the Transactions page — which is exactly how this test
 * failed the first time it ran. The first of the month and today are both always in range.
 */
function statementDatedThisMonth(): string {
  const now = new Date();
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  const content =
    'Date,Description,Amount\n' +
    `${iso(first)},COFFEE SHOP,-4.50\n` +
    `${iso(now)},PAYCHECK,"2,500.00"\n`;
  const dir = mkdtempSync(path.join(tmpdir(), 'finances-e2e-'));
  const file = path.join(dir, 'generic-statement.csv');
  writeFileSync(file, content);
  return file;
}
