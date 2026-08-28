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
- Statement files uploaded for parsing are retained only as long as the import needs them, then
  deleted or moved to encrypted storage. They are the single richest thing an attacker could take.
- Logs never contain transaction descriptions, balances, or account identifiers at INFO level.

## The AI service specifically

- It gets transaction data, not credentials. It has no database access and no outbound network
  access except to whatever inference endpoint is configured.
- If a hosted LLM is used for categorization or PDF extraction, understand exactly what leaves the
  machine. Sending a full statement to a third-party API is a real decision with a real answer —
  it is not automatically wrong, but it should be deliberate and documented here when made.
  Local inference via Ollama is the alternative and is a legitimate reason to keep a GPU around.

## Auth

- Legacy used `jjwt 0.9.1` with a symmetric secret. That library version has known vulnerabilities
  and none of that code is carried forward.
- Passwords: modern hashing (argon2id or bcrypt via Spring Security defaults). No exceptions.
- MFA before this app ever faces the public internet with real data in it. See D-12.

## Backups

Before real data goes in, there is a tested restore path. An untested backup is not a backup, and
a personal ledger with three years of categorization corrections in it is genuinely irreplaceable.
