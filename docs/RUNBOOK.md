# Runbook — running this for real

The ordered version of what SECURITY.md, DECISIONS.md and the Makefile say in pieces. Written for
the homelab move (D-16): one always-on box, reachable over Tailscale only, never publicly routable.
Every step here has been run on the dev Mac; the box-specific values are called out.

Before anything else: `make doctor`. It says what is installed, what is configured, and which
stacks are up.

---

## 1. First deployment on the box

1. Install Docker, `git`, `openssl`, `perl` (for `.env` edits) and Tailscale. Join the tailnet and
   note the box's Tailscale address (`100.x.y.z`).
2. Get the code there. Until the branch is pushed, that means a bundle: `make bundle` on the Mac,
   copy `backups/repo-*.bundle` over, then `git clone repo-….bundle finances_manager`.
3. `make ensure-env`. Then edit `.env`:
   * `DATABASE_PASSWORD` — a generated value, not the example one. (Nothing publishes the
     database port, but the example password is still not a password.)
   * `BIND_ADDR=100.x.y.z` — the Tailscale address and nothing else. This one line is how far the
     app, and the automation token, can be reached. Never `0.0.0.0`.
   * `WEBAUTHN_RP_ID` and `WEBAUTHN_ALLOWED_ORIGINS` — the hostname the browser will use and the
     full origins (`http://box.tailnet.ts.net:4200` and `:8080`). Passkeys are bound to these; a
     mismatch is a ceremony that fails with no useful error.
   * `COOKIE_SECURE=true` only if the app is served over HTTPS (Tailscale Serve can terminate
     TLS). Over plain HTTP a Secure cookie is never sent and nobody can sign in.
   * `APP_TIMEZONE` — where the person is. Containers run in UTC.
   * `BACKUP_COPY_TO` — a path off the box's own disk (a mounted drive, a synced folder).
4. `make backup-key`, then copy the key file into a password manager. Do this before the first
   real import: an archive encrypted without a copy of the key is noise.
5. `make up`. Flyway applies the schema on first start. `make doctor` should show four healthy
   containers on the dev stack, published on the Tailscale address.
6. Open the app from a tailnet device. The first screen is setup, not sign-in: create the one
   account. Let the browser save the passphrase; there is no reset.
7. Sign-in security → add a passkey from each device you will use. The first one turns the second
   factor on. Passkeys need `localhost` or HTTPS, so on plain HTTP over Tailscale this step waits
   for TLS.
8. `make mcp-token` and `make up` again if Claude Code will drive the box's API. Read the token
   section of SECURITY.md first: it satisfies the passkey factor, and `BIND_ADDR` is its whole
   boundary in Docker.

## 2. Backups

`make backup` is the one command. It dumps the database, copies `.env` beside it (the dump alone
cannot re-link an import: `ACCOUNT_KEY_SECRET` keys every account link), encrypts both into one
archive when the key exists, deletes the plaintext, and copies the archive to `BACKUP_COPY_TO`. It
says out loud when it could not encrypt or could not copy.

Schedule it. On macOS a `launchd` plist, on Linux a user cron line:

```
17 3 * * * cd /path/to/finances_manager && make backup >> backups/backup.log 2>&1
```

Keep about thirty archives; older ones can go. `backups/` is gitignored and `*.enc` is ignored
everywhere.

**Test a restore every month**, not only after a scare:

```
make restore FILE=backups/finances-<stamp>.enc
```

It decrypts into a private temp directory, loads the dump into the scratch compose project (never
the live database), and compares row counts table by table against the live database — every
table `pg_tables` knows about, so a table missing from the archive fails the check. "restore
verified: every table matches" is the sentence to look for. The scratch project's `db` container
is left running; `make e2e-down` removes it.

## 3. Upgrades

1. `make backup` first, and confirm the archive landed off-disk.
2. Bring the new code in (a pull once the branch is pushed; a bundle until then).
3. `make up`. Images rebuild, Flyway applies any new migration, the repeatable view migration
   re-applies. Watch `make logs` until `/actuator/health` answers.
4. Before changing the **PostgreSQL major version** in `infra/docker-compose.yml`: back up, then
   either run `pg_upgrade` against the volume (it is mounted at `/var/lib/postgresql` for exactly
   this) or dump and restore into a fresh volume. A major bump without a tested restore is a
   data-loss event waiting for a reason.

## 4. Rotating and revoking

* **The automation token.** Delete the `LOCAL_API_TOKEN` line from `.env`, `make mcp-token`,
  `make up`. Blank disables the filter entirely — revoking is deleting the line and restarting.
* **A passkey.** Sign-in security → Remove. Removing the last one returns the account to
  passphrase only, which is the recovery path when every authenticator is lost.
* **The passphrase.** There is no reset. Change it only while signed in, once that screen exists;
  until then it is a database update of `app_user.password_hash` with a bcrypt hash.
* **`ACCOUNT_KEY_SECRET` — do not rotate it.** Every import link was keyed under it, and an
  account linked under one secret is not found under another. See §5 for the loss case.

## 5. When something is lost

**Every authenticator is gone and the account demands a passkey.** From the box:

```
docker compose -f infra/docker-compose.yml --env-file .env exec db \
  psql -U finances -d finances -c "DELETE FROM user_credentials;"
```

The account drops back to passphrase only. Sign in, add a new passkey.

**`.env` is gone (a rebuilt box, a lost disk).** Restore it from the latest archive: `make restore`
prints the `env-<stamp>` file the archive holds; decrypt the archive by hand with
`openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass file:<key> -in <archive> | tar -x` and
put `env-<stamp>` back as `.env`. The database password, the token, the account key secret and
the passkey origins all come back together.

**The key file is gone and the archives are encrypted.** The copy in the password manager is the
only way in. There is no other.

**`ACCOUNT_KEY_SECRET` is gone but the database survived.** Imports will report every account as
"not set up yet" because no key matches. Do not create the accounts again — that doubles them.
Instead clear the links so the next import re-learns them by their last four digits:

```
psql … -c "UPDATE account SET external_id = NULL WHERE external_id IS NOT NULL;"
```

Then import each account's latest statement once. An account whose last four digits are not
unique stays unlinked and is reported; link it by hand by setting `external_id` to the key the
import reported under `unlinkedAccounts`.

**A reconciliation checkpoint is wrong and no file will replace it.** Dashboard → the mismatch row
→ Remove checkpoint, or the MCP `delete_checkpoint` tool. Importing the statement again records a
fresh one.

**A transaction was called a transfer and is not one.** Transactions → the row → Not a transfer.
Only a single-sided row can be un-marked; a two-legged manual transfer is removed and re-entered.

## 6. When it looks wrong

* `make doctor` first, then `make logs`.
* Every 4xx arriving as **401** — the error dispatch is being skipped by a filter. CLAUDE.md.
* An import failing with the parser's own sentence is a normal outcome; one failing with "could
  not be parsed" and nothing else means the AI container is down or unhealthy. `docker ps`.
* Passkeys refusing every ceremony — the origins in `.env` do not match the address in the
  browser's location bar, or the address is plain HTTP and not `localhost`.
* The API refusing to start with a message about `ACCOUNT_KEY_SECRET` or `APP_TIMEZONE` — the
  message names the variable; `make ensure-env` generates the first, the second is a zone id.
* Anything involving money that looks plausible but wrong — stop and check the reconciliation
  report before trusting a number. That is what the checkpoints are for.
