# Security notes

This system will eventually hold the complete financial picture of a person and a business. That
raises the stakes above a normal side project. None of the below is exotic — it's just non-optional.

## Status: this is a development environment

*Agreed 2026-08-27.* What runs today on `localhost:4200` is a **dev environment, not a system of
record.** The bank and the broker hold the authoritative data; this database is disposable and gets
truncated routinely during testing. Real exports may be used as test input — that is the point, and
it has already found bugs no synthetic file would have — but nothing here is relied upon, and losing
it costs nothing.

Two things make that true rather than merely intended:

* **Everything binds to loopback.** Docker publishes to `0.0.0.0` by default, which put the app and
  PostgreSQL on every interface — reachable by anything on the same wifi, with the database behind
  the password published in `.env.example`. Ports are now pinned to `127.0.0.1`, and the database
  port is not published at all: only the api container needs it, over the compose network.
* **The `.env` password is the documented example value.** That is acceptable *because* nothing is
  reachable off the machine and the data is disposable. It stops being acceptable the moment either
  of those changes.

**The browser tests truncate every table — of their own database.** `make e2e` runs against a
separate compose project (`finances-e2e`, ports 4201/8081) with its own volume, so the stack it
wipes is never the one in use. This replaced a guard-based approach: the suite used to truncate the
*dev* database and rely on a "does this look real?" check to stop itself, which is a procedural
defence against a destructive default. Playwright's defaults now point at the test stack, so a bare
`npx playwright test` cannot reach real data — it is not pointed at it. The guard remains as a
second line of defence, and `E2E_FORCE_RESET=1` still overrides it deliberately.

The suite also owns its account at `e2e@finances.invalid` rather than a real address, and tears it
down afterwards — a passing run used to leave an account behind that suppressed the first-run setup
screen, locking the owner out of his own app.

**Before this stops being a test environment**, all of the following must be true, and none is yet:
a real generated database password; a tested restore path (D-16 — an untested backup is not a
backup); passkeys enrolled and enforced (D-12); and reachability limited to Tailscale (D-16 — the
decision was homelab, VPN-only, **never publicly routable**).

## The local automation token (D-17)

`LOCAL_API_TOKEN` lets `services/mcp` — and therefore Claude Code — act as the account owner without
a browser session. It is an authentication bypass, deliberately, and is built as one:

* **Absent by default.** Blank disables the filter entirely. The bypass does not exist until a value
  is set, and an empty configured token cannot match an empty presented one.
* **Loopback only**, checked *before* the token is compared so a remote caller cannot learn anything
  from response timing. `getRemoteAddr()` only — never `X-Forwarded-For`, which the caller controls.
* **Constant-time comparison**, so a near-miss leaks no prefix.
* **At least 32 characters**, refused at startup otherwise. `make mcp-token` generates one.
* **Revoked** by clearing the line in `.env` and restarting the API.

**It satisfies the passkey second factor.** It has to, or it could not reach an API that requires
one — but that means registering a passkey no longer covers a caller holding this token. This is
acceptable only while the token is loopback-scoped and the deployment is VPN-only (D-16). If this
app ever becomes reachable more widely, this trade must be revisited before that happens, not after.

**In Docker the loopback check cannot work**, and the compose file turns it off. Docker rewrites the
source address of published traffic, so the API sees the bridge gateway and would refuse a caller
that genuinely is on this machine. What bounds reachability there is publishing the port to
`127.0.0.1` only — the same boundary the rest of the app already relies on, not a new one. Running
the API natively (`make api`) keeps the in-process check, which is why `true` remains the default.

The token never appears in a log. A token presented from a non-loopback address is logged once per
address, with the address and not the token, because the alternative is a 401 indistinguishable from
a wrong secret.

## Secrets

- No credential, token, or key is ever committed. `.env` is gitignored; `.env.example` documents
  every variable by name with a dummy value.
- Aggregator access tokens and any bank credentials are **encrypted at rest** in a dedicated table,
  never in application logs, never in a JSON response, never in an error message.
- Decide on a key management approach before the first real token is stored. Environment-variable
  keys are acceptable for a single-host deployment; hardcoded keys are not, at any stage, including
  "just for testing."

## Data handling

- Account numbers are stored masked (last four) unless there's a concrete reason for the full value.
- **The link between an imported row and an account is an HMAC of the account number under
  `ACCOUNT_KEY_SECRET`**, a per-install secret `make up` generates into `.env`. It used to be a
  bare truncated SHA-256, and with the last four stored beside it a ten-digit number came back out
  in a fifth of a second (review 2026-09-11, P7) — from the `account` table, from any response
  listing unlinked accounts, from whatever the MCP tools hand to the model. Keyed, the id is stable
  on one install and meaningless anywhere else. The AI service refuses to start without the secret
  rather than fall back. **The secret is part of the backup**: an account linked under one secret is
  not found under another, and the only recovery is clearing `account.external_id` so the next
  import re-links by mask. For one release the parser also sends the old key so existing links are
  re-keyed on their next import; that field and the branch that reads it come out afterwards.
- Statement files uploaded for parsing are retained only as long as the import needs them, then
  deleted or moved to encrypted storage. They are the single richest thing an attacker could take.
- Logs never contain transaction descriptions, balances, or account identifiers at INFO level.

## The AI service specifically

- It gets transaction data, not credentials for anything of Allen's. It has no database access.
  Its outbound connections are exactly three kinds, all named here: the market-data vendor
  (`data.alpaca.markets`, with `ALPACA_API_KEY`/`ALPACA_API_SECRET`, which live in this container
  and nowhere else), the paper broker (`paper-api.alpaca.markets`, only when
  `TRADING_BROKER=alpaca_paper`; the code refuses any other Alpaca host, and the live endpoint is
  not a configuration value in this release) and, later, the inference endpoint. Compose gives it no egress control yet, so
  "nothing else" is a rule rather than an enforced property; an internal-only network with a proxy
  is the homelab follow-up. The API's own outbound connection is to the ntfy topic in `NTFY_URL`,
  carrying a symbol and a price and nothing about accounts or balances.
- If a hosted LLM is used for categorization or PDF extraction, understand exactly what leaves the
  machine. Sending a full statement to a third-party API is a real decision with a real answer —
  it is not automatically wrong, but it should be deliberate and documented here when made.
  Local inference via Ollama is the alternative and is a legitimate reason to keep a GPU around.

## Auth

- **The login audit reads nothing the caller controls.** It records the connection's own address
  and never `X-Forwarded-For`. That header was once preferred, and a non-address value made the
  audit insert throw before the failure row was written — which switched lockout off, because
  lockout counts those rows. Behind a reverse proxy the recorded address is the proxy's; that is a
  known limitation and the honest one. A trusted-proxy setup can restore the original address from
  the proxy's side later, without ever trusting the client's.
- **Login rotates the session id.** A controller-based login does not get `formLogin`'s
  `ChangeSessionIdAuthenticationStrategy`; `AuthController` calls `changeSessionId()` itself, so a
  session id fixed before authentication is not valid after it.
- **The API logs at INFO unless `LOG_LEVEL` says otherwise.** It shipped at DEBUG, which is the
  level `AiServiceClient` uses for a rejected upload's body on the grounds that this document keeps
  statement content out of INFO. The default and the mitigation cancelled out.
- Legacy used `jjwt 0.9.1` with a symmetric secret. That library version has known vulnerabilities
  and none of that code is carried forward.
- Passwords: modern hashing (argon2id or bcrypt via Spring Security defaults). No exceptions.
- MFA before this app ever faces the public internet with real data in it. See D-12.

## Backups

Before real data goes in, there is a tested restore path. An untested backup is not a backup, and
a personal ledger with three years of categorization corrections in it is genuinely irreplaceable.

**What exists (2026-09-05, extended 2026-09-12):** `make backup` writes a `pg_dump` archive to
`backups/` (gitignored) and a copy of `.env` beside it, because the dump alone cannot re-link an
import: `ACCOUNT_KEY_SECRET` keys every account link. `make bundle` writes the whole repository as
one file there too — until the branch is pushed, this machine is the only copy of the code.
`make restore FILE=…` loads it into the throwaway e2e compose project — never the dev stack — and
then compares row counts, table by table, against the live database. That comparison is the test:
an archive that restores cleanly but is missing a table is precisely what "tested restore" is meant
to catch, and it is invisible unless something counts. First run: 14 tables, all matched.

**Encryption and the off-site copy (2026-09-24):** `make backup-key` generates a key outside the
repository (`BACKUP_KEY_FILE`, default `~/.config/finances/backup.key`); with it present,
`make backup` encrypts the dump and the `.env` copy into one `.enc` archive with
`openssl enc -aes-256-cbc -pbkdf2`, removes the plaintext, and copies the archive to
`BACKUP_COPY_TO` when that is set. Without the key it still backs up, in the clear, and says so
every time. `make restore` reads either form. The key belongs in a password manager: an archive
without it is noise. Confidentiality only — CBC has no integrity tag, so a tampered archive is
detected by the restore verification's row counts rather than by the cipher. `age` would be the
upgrade if that ever matters. docs/RUNBOOK.md has the schedule and the recovery steps.

## The AI service

Runs as an unprivileged user with a pinned `uv` on a floating `python:3.13-slim` base — the tool
that installs everything is pinned; the base image is not — with dependencies installed from the lockfile
(`--frozen`), and compose gives it a read-only filesystem, no capabilities and
`no-new-privileges`. It still has no authentication of its own and no upload-size cap of its own;
containment rests on it being unpublished and on the API's 10 MB limit in front of it. That is
adequate while it is reachable only over the compose network, and is written down here so it is a
known state rather than an assumption.

## Reachability, in one variable

`BIND_ADDR` in `.env` is where Docker publishes the web and API ports. Loopback means this machine.
On the homelab it should be the Tailscale address and nothing else. Because the in-process loopback
check on `LOCAL_API_TOKEN` cannot work inside a container, **that variable is also exactly how far
the token reaches** — the API logs a warning at startup saying so whenever the token is on and the
check is off.
