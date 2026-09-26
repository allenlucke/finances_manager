import { execFileSync } from 'node:child_process';

/**
 * Truncates the database before the suite runs.
 *
 * <p>Not tidiness. These tests share one long-lived database with the running stack, and without a
 * reset they are stateful in ways that bite: unique account names collide with the previous run,
 * and — found the hard way — a few failed sign-ins from a broken run trip the lockout policy and
 * every subsequent run fails with "not accepted" for fifteen minutes, which reads exactly like a
 * credentials bug and is not one.
 *
 * <p>Fails loudly rather than continuing. A suite that silently runs against dirty state produces
 * results nobody can trust.
 */
/**
 * The compose project holding the database these tests own.
 *
 * <p>Defaults to the isolated e2e project, never the dev stack. That default is the safety
 * property: a bare `npx playwright test` run by hand cannot reach the database Allen is using,
 * because it is not pointed at it. If the e2e stack is not running the suite fails saying so,
 * which is the right outcome — far better than quietly truncating real data.
 */
const PROJECT = process.env.E2E_COMPOSE_PROJECT ?? 'finances-e2e';

function psql(sql: string): string {
  return execFileSync(
    'docker',
    [
      'compose',
      '-p',
      PROJECT,
      '-f',
      '../../infra/docker-compose.yml',
      '--env-file',
      '../../.env',
      'exec',
      '-T',
      'db',
      'psql',
      '-U',
      process.env.DATABASE_USER ?? 'finances',
      '-d',
      process.env.DATABASE_NAME ?? 'finances',
      '-tA',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      sql,
    ],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  )
    .toString()
    .trim();
}

/**
 * Waits for the API to be serving before any test runs.
 *
 * <p>Checking that the web container answers is not enough: nginx serves the SPA the moment it
 * starts, while the API behind it may still be running migrations. Tests then race a half-started
 * backend and the first one fails with a 401 that looks exactly like a credentials bug. This is the
 * flake that reappeared after every container rebuild.
 */
async function waitForApi(baseUrl: string): Promise<void> {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseUrl}/actuator/health`);
      if (response.ok) {
        const body = (await response.json()) as { status?: string };
        if (body.status === 'UP') {
          return;
        }
      }
    } catch {
      // Not listening yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(
    `The e2e API at ${baseUrl} did not become healthy within 60s. Start its stack with \`make e2e\`.`,
  );
}

/**
 * The account the suite creates and owns. Anything else is somebody's real work.
 *
 * <p>Deliberately at `.invalid` — a TLD reserved by RFC 2606 precisely so it can never be a real
 * address. This used to be Allen's own email, which meant a leftover test account was
 * indistinguishable from one he had created himself: he found a sign-in screen for an account he
 * had no memory of making, and the passphrase was one only this file knew.
 */
export const TEST_EMAIL = 'e2e@finances.invalid';
export const TEST_PASSPHRASE = 'a-long-enough-passphrase';

/**
 * Second line of defence: refuses to wipe a database that looks like it is being used for real.
 *
 * <p>The first line is the compose project above — the suite talks to its own stack, so there is
 * normally nothing of Allen's within reach. This check stays anyway, because the cost of being
 * wrong is somebody's imported statements and the cost of the check is one query. If it ever
 * fires, something is misconfigured and stopping is the correct response.
 *
 * <p>"Looks real" is deliberately generous: any account other than the suite's own, or any import
 * batch that belongs to someone other than the suite's own account. (The suite does import now —
 * the upload journey is the product's primary workflow — so its own batches cannot be the signal;
 * anybody else's still are.) Set E2E_FORCE_RESET=1 to override, which is a deliberate act rather
 * than a default.
 */
function refuseIfDatabaseLooksReal(): void {
  if (process.env.E2E_FORCE_RESET === '1') {
    return;
  }

  const otherUsers = psql(
    `SELECT count(*) FROM app_user WHERE lower(email) <> lower('${TEST_EMAIL}')`,
  );
  const imports = psql(
    `SELECT count(*) FROM import_batch b JOIN app_user u ON u.id = b.user_id ` +
      `WHERE lower(u.email) <> lower('${TEST_EMAIL}')`,
  );

  if (otherUsers !== '0' || imports !== '0') {
    throw new Error(
      'Refusing to reset: this database has real use in it ' +
        `(${otherUsers} other account(s), ${imports} import(s)).\n` +
        'These tests truncate every table. Point them at a scratch database, or set ' +
        'E2E_FORCE_RESET=1 if you genuinely mean to erase it.',
    );
  }
}

export function truncateEverything(): void {
  const sql = `TRUNCATE
    categorization, holding, security, transaction, target, statement, import_batch,
    account, category, ledger_entity, app_user,
    login_attempt, user_credentials, user_entities,
    spring_session_attributes, spring_session
    RESTART IDENTITY CASCADE;`;

  try {
    psql(sql);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(
      'Could not reset the database before the e2e run. Is the stack up (`make up`)?\n' + detail,
    );
  }

  // Verified rather than assumed. A reset that quietly does nothing produces the most confusing
  // failure in the suite: sign-in is rejected because an account from a previous run still exists,
  // which reads exactly like an authentication bug.
  const remaining = psql('SELECT count(*) FROM app_user');
  if (remaining !== '0') {
    throw new Error(`Database reset did not take effect: ${remaining} user(s) remain.`);
  }
}

export default async function resetDatabase(): Promise<void> {
  // 8081, not 8080 — the e2e stack, matching PROJECT and the config's baseURL default.
  await waitForApi(process.env.E2E_API_URL ?? 'http://localhost:8081');
  refuseIfDatabaseLooksReal();
  truncateEverything();
  console.log('e2e: database reset (0 users)');
}

/**
 * Puts the database back the way the suite found it.
 *
 * <p>Without this the run ends with its own account still present, so the app shows a sign-in
 * screen for an account whose passphrase lives only in this file — the owner is locked out of his
 * own app by a passing test run. The same "does this look real?" guard runs first, so a teardown
 * can never destroy anything a test did not create; if it does look real, the data is left alone
 * and the run is not failed for it.
 */
export async function clearSuiteData(): Promise<void> {
  try {
    refuseIfDatabaseLooksReal();
    truncateEverything();
    console.log('e2e: database left empty — the app will offer its first-run setup screen');
  } catch (error) {
    console.warn(
      'e2e: leaving the database alone — it no longer looks like scratch data.\n' +
        (error instanceof Error ? error.message : String(error)),
    );
  }
}
