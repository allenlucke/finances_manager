# CLAUDE.md — finances_manager

> **Read this first, then `docs/DECISIONS.md`.** The stack is now settled — the seven provisional
> defaults left open by the M0 scaffold (D-10 through D-16) were decided with Allen on 2026-08-22.
> Treat `docs/DECISIONS.md` as binding, and read the *Why* under each entry before changing anything
> that depends on it.

---

## What this project is

A personal + small-business finance platform for Allen Lucke (Feeling Froggy LLC). It started in
2020–2021 as an Angular 12 / Spring Boot 2.4 / PostgreSQL budgeting app with a period-based budget
model. That code still exists, untouched, under `legacy/`. This branch is a ground-up modernization
that keeps the **domain model** and throws away the **implementation**.

The destination, in Allen's words: connect all of his finances — personal bank accounts, the Feeling
Froggy LLC business accounts, Fidelity brokerage — read statements, categorize transactions
automatically, and layer AI/ML on top of the whole picture.

### The three-phase arc

1. **Rebuild the budgeting core** on a current stack, importing the legacy domain model.
2. **Ingest real financial data** — statement files first (CSV/OFX/QFX/PDF), then aggregator APIs.
3. **Make it smart** — auto-categorization, anomaly detection, forecasting, and a natural-language
   layer over the user's own financial history.

`docs/ROADMAP.md` breaks this into concrete milestones.

---

## Repo layout

```
├── CLAUDE.md              ← you are here
├── docs/                  ← architecture, domain, roadmap, decisions, security
├── services/
│   ├── api/               ← Java + Spring Boot. System of record. Owns the database.
│   ├── web/               ← Angular SPA. Talks only to services/api.
│   ├── ai/                ← Python + FastAPI. Statement parsing, categorization, ML/LLM work.
│   └── mcp/               ← Python. MCP server so Claude Code can drive the app (D-17).
├── infra/
│   └── docker-compose.yml ← postgres + api/web/ai for local dev
├── .mcp.json              ← registers services/mcp with Claude Code
├── legacy/                ← the 2021 codebase, frozen. Reference only. Never build it.
└── .github/workflows/     ← CI
```

**Rule:** `services/api` is the only thing that talks to PostgreSQL. `services/ai` is a stateless
worker that receives documents/transactions over HTTP and returns structured results. The web app
never calls the AI service directly. `services/mcp` calls only `services/api`, adds no rules of its
own, and is not part of the compose stack — Claude Code launches it.

---

## Working agreements

- **Ask before you swap a pinned version or add a heavyweight dependency.** Versions in this
  scaffold were pinned in August 2026 and are current-stable on purpose.
- **Migrations are additive and forward-only.** Never edit a migration that has been applied.
  New file, new version number.
- **No secrets in the repo, ever.** `.env.example` documents every variable; `.env` is gitignored.
  This project will eventually hold real bank credentials and tokens — see `docs/SECURITY.md`
  before writing anything that touches an access token.
- **Money is `NUMERIC(19,4)` in Postgres and `BigDecimal` in Java.** Never a float, never a double,
  in any language, at any layer. The legacy schema used `NUMERIC(12,2)`; the new one widens it.
- **Every timestamp is `TIMESTAMPTZ` and stored in UTC.** Display conversion happens in the client.
- **Tests are part of "done."** A feature PR with no tests is not finished. The legacy repo had
  ~150 stub `.spec.ts` files that never got written; don't repeat that.
- **Prefer boring.** This is a system that handles real money for one real person. Novelty is a cost.
- **Audit records get their own transaction.** Anything written to explain a failure —
  `import_batch` status, `login_attempt` — must use `REQUIRES_NEW`, or the rollback that follows the
  failure destroys the evidence of it. This has bitten twice. On `login_attempt` the consequence is
  worse than a missing row: failures are what lockout counts, so losing them makes brute-force
  protection **fail open**, silently. `AuditDurabilityTest` pins it.
- **Anything calling `services/ai` must pin HTTP/1.1.** Uvicorn speaks HTTP/1.1 only, while the
  JDK's `HttpClient` defaults to HTTP/2 and opens cleartext connections with an h2c upgrade attempt.
  The upgrade fails, the request framing is mangled, and a multipart body arrives with no parts —
  reported by FastAPI as a *missing field*, which points at entirely the wrong thing. Also never set
  `Content-Type: multipart/form-data` by hand: that pins the header without a boundary and produces
  the identical symptom. Let Spring's form converter write it.
- **Never signal an auth condition by throwing from `loadUserByUsername`.**
  `DaoAuthenticationProvider` catches everything that method throws except `UsernameNotFoundException`
  and rewraps it as `InternalAuthenticationServiceException` — so a `LockedException` raised there
  arrives at the caller looking like a server fault and gets answered as bad credentials. Report the
  condition on the returned `UserDetails` (`accountLocked`, `disabled`) and let Spring's own
  pre-authentication checks raise it; those still run before the password is compared, so nothing is
  leaked by timing. `GapsClosedTest.lockoutAfterRepeatedFailures` asserts the 429.
- **Test fixtures never use Allen's real identifiers.** The browser suite used to create an account
  under his own email address, so a leftover test account was indistinguishable from one he had made
  himself — he hit a sign-in screen for an account he had no memory of creating, whose passphrase
  lived only in a spec file. Suite-owned data is now at a reserved `.invalid` domain, and the suite
  clears up after itself so a passing run never leaves an account nobody can sign in as.
- **A security filter must handle the ERROR dispatch.** `OncePerRequestFilter` skips it by default,
  and Spring sends every controller error back through the chain. A filter that authenticates a
  request will not re-authenticate the error dispatch, so the context is empty by the time the
  authorization filter sees `/error` — and *every* 4xx comes back as **401**. "No such transaction"
  then reads as "your credentials are wrong", which sends you to check the one thing that was never
  broken. Override `shouldNotFilterErrorDispatch()` to return false.
- **`getAuthentication() == null` is not "nobody is signed in."** `AnonymousAuthenticationFilter`
  puts an `AnonymousAuthenticationToken` in the context for every unauthenticated request, so that
  check is false almost everywhere and any filter guarded on it silently does nothing. Use an
  `AuthenticationTrustResolver`.
- **Nothing on the authentication path reads a header the caller controls.** `X-Forwarded-For`
  was read into the login audit and cast to `inet` on insert; a non-address value made the insert
  throw inside the authentication event, the failure row was never written, and lockout — which
  counts rows — never fired. Five wrong passwords with `X-Forwarded-For: nope`, then the right one:
  signed in. Reproduced 2026-08-29. The audit row *is* the control, so its write must not depend on
  anything a caller can put in a request. `GapsClosedTest.lockoutSurvivesAHostileForwardedHeader`.
- **One dedupe implementation, in the API.** The parser still emits a `dedupe_key`; the API does
  not read it. Two implementations in two languages drifted three ways without any test noticing
  (signed vs. magnitude amount, reference-number stripping, account id vs. account hash), and the
  first of those meant no debit entered by hand could ever collide with the same debit imported
  later. `TransactionService.dedupeKey` is the identity; `ImportIdentityTest` pins it.
- **A refund is not a transfer.** A transfer is *not spending* and stays uncategorizable by CHECK
  constraint. A refund is *negative spending* and must be bookable against the category it refunds.
  The parser reports them as two flags (`is_probable_transfer`, `is_probable_refund`) and the API
  only ever marks the first as a transfer. Folding them together made every refund uncategorizable
  forever and left the category overcharged.
- **A failed request is never an empty state.** Four screens used to render "No accounts yet.
  Add one" when the accounts request failed, and the dashboard turned a missing net worth into
  `$0.00`. For someone with years of statements that is the worst possible message. Every request
  sits behind `LoadState` (value / error / loading) and templates branch on all three; a bare
  array plus a boolean cannot tell "nothing here" from "could not ask".
- **A controller's refusal must go through `ApiExceptionHandler`.** Boot's default error body
  omits the exception message, so a `ResponseStatusException(422, "why")` reaches the browser as a
  bare status and the screen shows a generic fallback. The handler turns them into problem details
  with the reason in `detail`, which is the field the web reads. The first HTTP-level import test
  found every import refusal arriving wordless.
- **A CSV row is judged by its date, not its width.** A row with a date is always attempted and
  its failure reported with a line number; a narrow row with no date is footer prose. Dates are
  resolved once per file, never per row — per-row first-fit read `03/04` and `25/04` in the same
  file as different calendars without noticing. Money goes through the one parser in
  `ingest/common.py`; `84,31` is refused, not read as 8,431.
- **A running-balance column decides which end of a file is newest; dates only break a tie.**
  Comparing the first and last dates read a one-day export backwards, so a newest-first file
  stored its oldest balance as the closing balance and the same figure as the opening — no
  warning, and a statement is unique per period with nothing to delete it, so the wrong checkpoint
  was permanent. `_checkpoint_balances` walks each known balance back to a balance-before-everything
  under both orderings; the ordering where every balance agrees is the file's. When nothing
  resolves it, no balance is recorded and the file says so.
- **What the parser says reaches the person.** Row warnings ("line 3: unrecognized date …") and
  the 422 verdicts ("No parser matches this file …") were counted into a log line and replaced
  with one fixed sentence, so an import that lost rows looked identical to one that did not, and
  the one HTTP test claiming otherwise mocked the sentence it asserted. Warnings live on
  `import_batch.warnings` and on `ImportResult`; a parser's 422 `detail` is the
  `AiServiceException` message. `AiServiceClientTest` drives a real HTTP server, not a mock.
- **Merchant rules live in `merchant_rules.json` and carry their own examples.** Every rule lists
  descriptions it must match and near-misses it must refuse, and a table test runs both. A rule
  that cannot match a real description cannot be added. Confidences are one scale across tiers;
  arbitration is by confidence alone.
- **A message a person has to read is not tested until something reads the DOM.** The setup form
  had a mismatch test that passed for weeks while the screen said nothing: it asserted the form was
  invalid and that nothing was posted, both true, while the error text was never rendered. Assert
  the text is present, and assert it is absent when it should be — the pair is what proves it.
  (The cause: a group-level validator's error never reaches `MatFormField`, which reads the
  *control's* error state. An `ErrorStateMatcher` bridges them.)
- **A second factor must add to the first.** Spring's `AbstractAuthenticationProcessingFilter`
  puts the new authentication in an *empty* context on success, so a passkey presented on a
  password session replaced the password factor instead of joining it, and the session was still
  one factor short — a different one. `FactorMergingAuthenticationManager` wraps the WebAuthn
  login filter's manager and unions the authorities for the same user. Only the browser suite's
  real ceremony could show this; `PasskeyMfaConfig` tests seed rows and never sign in twice.
- **Sign-out discards the CSRF cookie; fetch a new token before the next sign-in.** The token
  was only primed at bootstrap, so sign out then sign in without a reload was a 403 that the form
  blamed on the passphrase. `Auth.logout` re-primes, and a 403 on sign-in is reported as a stale
  page, never as bad credentials.
- **Read `TIMESTAMPTZ` as `OffsetDateTime`, never `Instant`, from a `ResultSet`.** The Postgres
  driver refuses the Instant conversion, and a row mapper that only runs when rows exist hides
  the failure until the first real row. The passkey list did exactly that.
- **Passkey settings must reach the container.** `WEBAUTHN_RP_ID`, `WEBAUTHN_ALLOWED_ORIGINS` and
  `COOKIE_SECURE` are passed through compose and set per stack; the e2e stack on 4201 needs its
  own origins or no ceremony can succeed there.
- **Browser tests run on their own stack, never the dev one.** `make e2e` builds a separate compose
  project (`finances-e2e`, ports 4201/8081) with its own volume, and Playwright's defaults point
  there. The suite truncates, and a truncate against a database in use destroys real statements with
  no undo — so the separation is structural rather than a guard promising to behave.

## Commands

All from the repo root:

```bash
make up          # docker compose up: postgres + api + web + ai
make down        # tear it all down
make db          # postgres only, for running services natively
make api         # run the Spring Boot API on :8080
make web         # run the Angular dev server on :4200
make ai          # run the FastAPI service on :8000
make test        # run every test suite
make e2e         # browser tests on their own throwaway stack (4201/8081)
make e2e-down    # dispose of that stack
make mcp-token   # generate LOCAL_API_TOKEN into .env, for Claude Code (D-17)
make backup      # pg_dump the dev database to backups/
make restore FILE=backups/x.dump   # restore into the scratch stack and verify row counts
make nuke        # like clean, but also deletes the dev database volume (asks first)
make fmt         # format everything
```

## Where things live

| Concern | Path |
|---|---|
| Domain entities | `services/api/src/main/java/llc/feelingfroggy/finances/domain/` |
| REST controllers | `services/api/src/main/java/llc/feelingfroggy/finances/api/` |
| DB migrations | `services/api/src/main/resources/db/migration/` |
| Angular features | `services/web/src/app/features/` |
| Statement parsers | `services/ai/src/finances_ai/ingest/` |
| MCP tools for Claude Code | `services/mcp/src/finances_mcp/server.py` |
| Categorization | `services/ai/src/finances_ai/categorize/` |

---

## First session checklist

When Allen starts a session on this repo, the useful opening moves are:

1. Read `docs/DECISIONS.md`. **All of D-10 through D-16 were resolved on 2026-08-22** — there are no
   OPEN items left. Do not re-litigate them; several carry downstream consequences recorded in that
   file, so re-opening one means re-checking what depends on it.
2. Read `docs/DOMAIN.md` — the legacy budget model is more subtle than it looks, especially the
   credit-card handling. Confirm the parts he still wants before you migrate them.
3. Confirm milestone 1 scope in `docs/ROADMAP.md`, then build it.

Do not start writing feature code before steps 1–3.

### Carried into M1 from the decisions session

* **Reporting SQL lives in Flyway-created views** (D-11), not `@Query` strings. JPA will not
  generate the balance-sheet queries; that is expected, not a surprise.
* **CSRF protection must come back on** when session cookies land (D-12). The scaffold's
  `SecurityConfig` disables it, which is correct for M0 and wrong for cookies.
* **The transaction schema must be aggregator-ready in M2** (D-14) — pending-transaction linkage
  above all, or the dedupe key double-counts pending→posted.
* **A tested restore path precedes real data** (D-16). It does not depend on the hosting choice.
