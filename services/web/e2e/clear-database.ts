import { clearSuiteData } from './reset-database';

/** See `clearSuiteData` — the suite does not leave an account behind that nobody can sign in as. */
export default async function globalTeardown(): Promise<void> {
  await clearSuiteData();
}
