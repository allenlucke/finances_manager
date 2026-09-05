import { expect, Page } from '@playwright/test';
import { TEST_EMAIL, TEST_PASSPHRASE } from './reset-database';

/**
 * Shared steps for the browser suite. Kept in one place so every spec signs in the same way and
 * waits for navigation the same way — the two places these tests used to go flaky.
 */

// Shared with the reset guard on purpose: it decides what counts as the suite's own data, and if
// the two ever disagree the guard stops recognising the account this file creates.
export const EMAIL = TEST_EMAIL;
export const PASSPHRASE = TEST_PASSPHRASE;

/**
 * Signs in, creating the account first if the database is empty.
 *
 * <p>Waits for one of the two headings before deciding which form it is looking at. Checking too
 * early is not a hypothetical: while auth state is still `unknown` the shell renders only a
 * progress bar, so an immediate `isVisible()` returns false, the helper takes the sign-in branch,
 * and it then fills two of the three setup fields and clicks a button that isn't there.
 */
export async function signIn(page: Page): Promise<void> {
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

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: /Account menu/ }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
}

/**
 * Navigates and waits for the destination to actually render.
 *
 * <p>Clicking a link and immediately touching a field is a race, and Playwright's auto-waiting does
 * not save you from it: several screens have a "Name" field, so a locator resolves happily against
 * the page you are *leaving* and the fill lands there. Waiting on something unique to the
 * destination is what makes the navigation observable.
 */
export async function goTo(page: Page, link: string, anchor: string): Promise<void> {
  await page.getByRole('link', { name: link }).click();
  await expect(page.getByText(anchor).first()).toBeVisible();
}

export async function addAccount(page: Page, name: string, type: string): Promise<void> {
  await goTo(page, 'Accounts', 'Add an account');
  await page.getByLabel('Name').fill(name);
  await page.getByRole('combobox', { name: 'Type' }).click();
  await page.getByRole('option', { name: type, exact: true }).click();
  await page.getByRole('button', { name: 'Add account' }).click();
  await expect(page.getByRole('cell', { name, exact: true })).toBeVisible();
}
