# Roadmap

Milestones, not dates. Each one ends with something Allen can actually use.

## M0 — Scaffold stands up *(this commit)*
`make up` brings postgres, api, web, and ai online. Health checks pass. CI is green. Nothing does
anything useful yet, and that's fine.

**Done when:** all four containers healthy, `/actuator/health` and `/health` return OK, the Angular
app loads and shows live status from both backends.

## M1 — Budgeting core, reimagined

**Scope revised 2026-08-22.** The original wording was "do everything the 2021 app did." That is no
longer the goal: Allen's call is that the old workflow was a chore, so the domain is rebuilt rather
than ported. See `docs/DOMAIN.md` → *The rebuilt model* for what changed and why. The legacy
reporting SQL (`legacy/db/budgetBalanceSheet.sql`, `acctBalScratch.sql`) is still the reference for
*what the numbers mean* — not for how they are computed.

Split into two halves. The seam is deliberate: it front-loads the decisions that are expensive to
reverse, and gives Allen something exercisable before any UI exists.

### M1a — Schema, auth, API, reporting

1. **Flyway `V2`** — the rebuilt core: `app_user`, `ledger_entity`, `institution`, `connection`,
   `account`, `category`, `transaction`, `target`, `statement`, `import_batch`, `categorization`.
   Aggregator-ready per D-14. Credit-card double-count rule enforced by CHECK constraint.
2. **Auth** (`V3`) — Spring Security 7, session cookies + WebAuthn passkeys per D-12. CSRF back on.
3. **REST + JPA** — accounts, categories, targets, transactions.
4. **Manual transaction entry** — so the system is usable before M2's import exists.
5. **Reporting as Flyway-created SQL views** per D-11 — spend vs. target by date range, account
   balances via window function (never materialized — see the `accountTracker` lesson in
   `docs/DOMAIN.md`), net position.

**Done when:** Allen can create accounts and categories, enter transactions, set targets, and pull
correct spend-vs-target and balance figures over an arbitrary date range — authenticated, tested,
and reconciling against a statement closing balance.

**Status (2026-08-24): M1a complete.** 45 Java tests green against Postgres 18 via Testcontainers.
Verified end to end over HTTP: first-run setup → login → accounts → categories → target →
transactions → balances, net worth, and spend-vs-target.

All four gaps from the first pass are closed:

* **Manual transfers write both legs**, linked by a shared `transfer_group_id`, so paying a card
  reduces what the card says you owe. Deleting either leg removes both — half a transfer would make
  money appear to leave one account without arriving anywhere. Import stays single-sided, because
  there each statement supplies its own leg.
* **Passkeys are a self-enforcing second factor.** No configuration flag: the requirement is
  derived from whether a credential is registered. No passkey → password alone (which it must be,
  since enrolling the first one requires being signed in); a passkey registered → both factors, at
  once, on the existing session. Removing the last passkey drops back to single factor, which is
  the recovery path when every authenticator is lost.
* **Login lockout** — five failures in fifteen minutes, counted only since the last success so
  signing in clears the count, and time-based rather than sticky (a permanent lock on a single-user
  system locks out its only administrator). `login_attempt` audits successes too.
* **`/api/v1/status` is gone**, replaced by the real dashboard.

### M1b — UI

6. **Angular Material** per D-13 — dashboard, transaction list, accounts, categories and targets.

**Status (2026-08-27): built and exercised in a real browser.** Six screens on Angular Material 22,
lazily routed, 15 unit tests and 7 Playwright journeys green.

Running it for the first time found five real defects that no API test could have caught:

* **Signing in bounced you straight back to the login screen.** `login` called `auth.refresh()` and
  navigated immediately; the route guard then read the *previous* auth state, which is not
  `unknown`, so it passed the filter and redirected. The nav bar rendered over the login form.
  `refresh()` now returns an observable that completes once state has settled, and callers wait.
* **The REST API spoke Java enum names.** The UI sent `direction: "debit"` — the spelling Postgres,
  the Python service and the TypeScript client all use — and Jackson wanted `DEBIT`, so *every*
  transaction the UI created came back 400. Fixed with `@JsonValue`/`@JsonCreator` on the coded
  enums: lenient in, canonical lowercase out, which is what `CodedEnum` existed to guarantee.
* **Ticking "Transfer" did nothing.** `computed(() => control.value)` over a reactive form control
  never re-evaluates — a form control is not a signal, so there is nothing to track. Replaced with
  `toSignal(control.valueChanges)`.
* **A created category vanished into a dropdown** with nowhere to see it. The Budget screen now
  lists categories.
* **Fields flashed red "required" errors immediately after a successful save**, because
  `patchValue` leaves a control touched. Now `reset`.

Two smaller ones: over-budget progress bars rendered the same blue as under-budget (Material 3
ignores `[color]="warn"`, and the token override loses to Material's own rule — the fix has to be
global, since view encapsulation stops a component style reaching Material's inner element), and
`mat-icon` ligature text was leaking into button accessible names, so a screen reader announced
"add Add". Roboto and Material Symbols are bundled locally rather than pulled from Google Fonts,
because a VPN-only host (D-16) has no outbound internet and a CDN font would simply never load.

* **Login / first-run setup** — which one appears is decided by the API, not by a link the user has
  to find. A fresh database has no account to sign in with. Also renders a distinct
  `passkey-required` state, because sending someone back to a password form they already satisfied
  is the kind of dead end that makes people switch MFA off.
* **Dashboard** — net worth (combined and per entity), account balances, this month's spend against
  target, and a prominent warning for any statement that does not reconcile.
* **Transactions** — date-range ledger, inline recategorization, and an entry form where ticking
  "Transfer" swaps the category field for a destination account and clears the category, mirroring
  the server rule rather than letting the user hit a 422.
* **Accounts** and **Budget** — creation forms plus balances, targets in effect, and closed targets
  kept visible as budget history.

**Done when:** Allen would rather use this than a spreadsheet.

Remaining in M1b:

* **The WebAuthn ceremonies are still Spring Security's own endpoints**, linked to rather than
  driven from the SPA. Registering and presenting a passkey needs the browser
  `navigator.credentials` calls wired into an Angular service.
* ~~No passkey management screen yet~~ — built (`/security`), and as of 2026-09-05 it says when it
  could not check rather than asserting "passphrase only".
* **The review queue is unused.** `GET /api/v1/transactions/review` is implemented and indexed, but
  it stays empty until M2/M3 produce uncategorized rows, so no screen reads it yet.
* **Statements are API-only.** `v_statement_reconciliation` drives the dashboard warning, but there
  is no UI for entering a closing balance; that arrives with M2's import.
* **Initial bundle is ~540 kB**, over the default 500 kB budget, which was raised to 750 kB rather
  than silenced. The bulk is the full Material Symbols icon font for about eight icons — subsetting
  it is the obvious win if that ever matters on a LAN.

### Testing

**Browser tests (`make e2e`)** run against a live stack via Playwright, deliberately outside
`make test`: they need four containers and a real database, and a suite that silently passes when
the stack is down is worse than none. `globalSetup` waits for the API to report healthy and
truncates the database first — both learned the hard way. Without the health wait, nginx serves the
SPA while the API is still migrating and the first test fails with a 401 that reads exactly like a
credentials bug; without the truncate, a broken run leaves the test account locked out for fifteen
minutes and every later run fails the same way.

Testcontainers against **Postgres 18** — not H2. The reporting views lean on Postgres-specific SQL
(window functions, `daterange` exclusion constraints) and an in-memory stand-in will lie.

## M2 — Statement ingestion
CSV first (the legacy Django `reader_chase.py` shows the shape), then OFX/QFX, then PDF. The
`transaction` table, the `import_batch` table, idempotent re-import, and a review UI for
unmatched rows. No AI yet — deterministic parsing only.

**Done when:** Allen drops a real Chase export and a real Fidelity export in and the transactions
land correctly, twice in a row, with no duplicates.

**Status (2026-08-26): CSV and OFX/QFX complete, end to end through the real Python parser.**
Uploading the same statement twice adds nothing the second time, in both formats. PDF is still to
come.

OFX earns its place over CSV by removing guesses rather than by parsing more rows:

* **`FITID`** — the institution's own permanent transaction id, kept as `external_id`. It survives a
  description later being cleaned up, which a hash of date/amount/description does not.
* **A period and closing balance**, which become a `statement` checkpoint. An imported OFX now
  reconciles to **zero** against the bank's own figure — the ledger proves itself instead of being
  assumed correct. This is what `v_statement_reconciliation` was built for and had nothing to check
  against until now.
* **`TRNTYPE`**, used conservatively: only `XFER` is treated as a transfer. `PAYMENT` is deliberately
  excluded because it is ambiguous without knowing the account — a card payment on a credit card,
  but usually a real bill payment on a checking account. A false positive here silently removes a
  genuine expense from the budget, which is far worse than a card payment landing in the review
  queue.

**Dependency added:** `ofxtools` 1.1.1 (actively maintained, no runtime dependencies of its own).
OFX is SGML-ish with a header block, optional closing tags and per-institution quirks — the kind of
format where a hand-rolled reader works on the sample file and fails on a real export. The trade is
strict spec validation, so a malformed file raises rather than yielding partial data; for money that
is the right failure, but it means some real-world exports may need handling.

* **The file's own row type is now used.** `looks_like_non_expense()` had been parsed and thrown
  away since M0. Chase marking a row `Payment` or `Return` is the institution stating what it is,
  which beats any regex over the description — so imported card payments are flagged as transfers
  and never counted as spending. The categorizer checks it ahead of its own patterns.
* **Import is idempotent at the database**, not by convention: the unique index on
  `(account_id, dedupe_key)` covers soft-deleted rows too, so deleting an imported row is not undone
  by re-importing.
* **A failed import leaves a record.** Found by test: the batch row was written inside the same
  transaction that applies the rows, so a rollback destroyed the evidence of the failure it was
  describing. `ImportBatchRecorder` now writes batch lifecycle in `REQUIRES_NEW` transactions.
* **The wire contract is pinned by a fixture** captured from the real Python service
  (`fixtures/parse-result-chase.json`). The Java DTOs are hand-mirrored from `finances_ai.models`,
  and a renamed field would otherwise deserialize to null and import as missing data.

### What real bank files changed (2026-08-27)

> **This repository is public.** The findings below came from real exports, so they are recorded as
> *shapes and counts* — never balances, amounts, account digits, or the names on an account. Row
> counts and ratios carry the engineering lesson and identify nobody; the figures would identify
> somebody and teach nothing. Every fixture in `services/ai/tests/` is synthesized on the same
> principle: real quirks, invented data.

Allen supplied a real Community America checking export. It parses cleanly — 40 transactions, no
warnings — but only after three fixes, and it disproved an assumption that had a test defending it.

* **Metadata above the header.** The file opens with `"Account Name : ..."`, `"Account Number : ..."`
  and `"Date Range : ..."` before the real header row. A reader that assumes row one is the header
  sees one nonsense column and rejects the file. The header is now searched for, and the lines above
  are kept — they carry the statement period, which nothing else in the file does.
* **Split debit/credit columns** instead of one signed amount, with debits *already negative*.
  Negating unconditionally would have turned every withdrawal into a deposit.
* **The closing balance is not the last row.** This export is newest-first, so the final row holds
  the *oldest* balance. Reading it as the closing balance produced a figure wrong by a full month's
  movement — and a plausible-looking one, which would have had the reconciliation view accusing a
  correct ledger of being out. The balance is now taken from the newest-dated row, whichever end of
  the file that is. (Figures deliberately not quoted: this repo is public. See the note at the top
  of this section.)
* **The merchant is in the memo.** Eight of forty rows had the description "Point Of Sale
  Withdrawal" with the actual merchant only in the memo field. Categorizing on the description alone
  left them permanently unidentifiable, so the merchant string is now built from both.

**The assumption that was wrong.** A test asserted that any row containing "AUTOPAY" is a transfer —
true on a card statement, where autopay means paying the card. On a checking account it means
auto-paying a bill, and it matched `ACH PAYMENT EVERGY METRO ... AUTOPAY`: an electric bill,
silently removed from spending. Two changes followed:

1. The bare `AUTOPAY` rule is gone. "Thank you" now has to accompany a payment word to count.
2. **Merchant rules run before transfer patterns.** Identifying *who* was paid is stronger evidence
   than phrasing describing *how*. With the weaker check first, a named utility lost to a generic
   word.

Also found: **eight transfers to Fidelity Brokerage in a single month** (`ACH PAYMENT FID BKG SVC
LLC`) were being counted as spending. Money moved into an investment account has not been spent, and
counting it inflates the budget by everything being invested.

On this real month the rules now reach **14 of 40 categorized, 9 correctly excluded as transfers,
17 to review** — a genuine measurement rather than an estimate, and the baseline M3 has to beat.

### Fidelity Accounts History (2026-08-27)

The brokerage transaction export, verified against a real file: 32 rows, 5 accounts, no warnings.

**A brokerage history contains no spending.** Every row is cash moving in or out, or an investment
action — a purchase, an exchange. Importing it as ordinary expenses inflates a budget by the entire
amount being invested, which in a single month was several multiples of the entire monthly budget.
All 32 rows are correctly excluded, and a test asserts that property over the whole file rather than
row by row, because one leak distorts a budget by its full value and brokerage amounts are large.

Three things this format forced:

* **The `Description` column is useless** — it reads "No Description" on every cash movement. The
  real text is in `Action`, which is also the type: free text like `YOU BOUGHT ... (Cash)` rather
  than a short enum, so non-expense detection gained regex patterns alongside exact matching.
* **One file, several accounts.** `ParsedTransaction` now carries `account_mask` and a hashed
  `account_key` for exports that name an account per row, and the dedupe key is scoped to it — two
  children receiving the same amount on the same day is ordinary and must not merge into one row.
* **Footer prose** arrives as one-field rows. Without skipping narrow rows, each became a warning
  about an unreadable date.

**The cross-file check that validated the design.** The checking export showed 8 outflows to
Fidelity; the brokerage export showed the matching deposits. All **8 of 8 matched** on amount and
date — the same movements seen from both sides. That is exactly what `transfer_group_id` exists to
link, and why neither side may be counted as spending.

Where the three real files now stand:

| File | Format | Rows | Warnings | Result |
|---|---|---|---|---|
| Community America checking | `cacu` | 40 | 0 | 14 categorized, 9 transfers, 17 to review |
| Fidelity Accounts History | `fidelity_history` | 32 | 0 | all 32 correctly non-spending |
| Fidelity Portfolio Positions | `fidelity_positions` | 29 | 0 | 8 accounts of holdings |

### Two bugs found by importing real files (2026-08-27)

**Dedupe silently lost four transactions.** A first import of a real month reported
`added=36 skipped=4` — four transactions discarded as duplicates on a completely empty database.
They were not duplicates: three identical transfers to the same payee on the same day, and three
more on another, identical in date, amount and description. Only the bank's Transaction Number
distinguished them, and the dedupe key ignored it.

The key now **uses the institution's own transaction id whenever the export provides one**, falling
back to the date/amount/description hash only for formats that do not (a Chase card CSV). This is
what an id is for; hashing visible fields cannot separate transactions that are genuinely identical
on all of them. The failure mode is the worst kind — money missing from a ledger with nothing to
indicate anything happened.

**Making people type their own account digits.** The first version of account linking required the
user to know the last four digits of their account and enter them into a "mask" field, because that
was how imports matched. Every one of these files already names its own account — CACU in a metadata
line above the header, Fidelity on every row. The parser now reads it, and an import that finds
unknown accounts returns them with the institution's own names for the UI to offer to create:

> This file covers 5 accounts you haven't set up yet.
> **Long Term Investment (Joint WROS - TOD)** ending 1111 · 4 transactions …
> [Create these accounts and import]

Creating them records the stable key, so every later import matches exactly with nobody typing
anything.

**A build lesson.** The test that broke when a record gained a field compiled fine locally and failed
in the container. Maven's incremental build skipped recompiling tests because only main sources had
changed; the container's clean build caught it. `mvnw compile` is not evidence that the image will
build — and the image kept serving old code while reporting success.

### The HTTP/2 bug, recorded because it will happen again

Statement upload failed with FastAPI reporting the `file` field as *missing*, which points squarely
at the client's form building. It was not that. The JDK's `HttpClient` defaults to HTTP/2 and, over
cleartext, opens with an h2c upgrade attempt; uvicorn speaks HTTP/1.1 only, logged
`Unsupported upgrade request`, and the mangled framing meant the multipart body arrived with no
parts at all.

It was invisible until M2 because the health check has no body to mangle. `AiServiceClient` now pins
HTTP/1.1, and `AiServiceMultipartTest` asserts against the JDK's own HTTP/1.1 server that a boundary
is present and the `file` part actually arrives.

A second, independent half of the same failure: setting `Content-Type: multipart/form-data` by hand
pins the header *without* a boundary, so the receiver finds no parts either. Spring's form converter
must be left to write that header itself.

### Being locked out of your own app (2026-08-28)

Allen set a passphrase, got locked out within minutes, and had no way back in. Three separate
defects stacked up, and none of them was the lockout policy itself doing anything wrong.

**The setup form had no confirmation field.** A passphrase typed once, never echoed, becomes the
only way into the account. A single typo is unrecoverable and gives no sign it happened — the next
sign-in just fails.

**The browser never offered to save it.** This is the part that looks like a browser problem and
isn't. Chrome and Safari decide to offer a save from a *form submission that navigates*; an SPA
posts with `fetch` and re-renders, so the heuristic never fires and no prompt appears. The fix is to
ask explicitly — `navigator.credentials.store()` with a `PasswordCredential`, feature-detected,
after a successful login and after setup. A second-order effect: the missing confirm field also made
the form look less like account creation to the password manager. `services/web/src/app/core/passwords.ts`.

**The lockout lied about itself.** Being locked out was reported as *"That email and passphrase
combination was not accepted"* — indistinguishable from a wrong passphrase, so the natural response
is to keep trying, which extends the lockout. The intent was to return 401 for bad credentials and
something distinct for a lock, and `AppUserDetailsService` did throw `LockedException` — but from
inside `loadUserByUsername`, where `DaoAuthenticationProvider` catches everything except
`UsernameNotFoundException` and rewraps it as `InternalAuthenticationServiceException`. The lock
arrived at the controller disguised as a server fault and fell through to the 401 branch. Reporting
it as `accountLocked` on the returned `UserDetails` instead lets Spring's pre-authentication check
raise it properly; that check still runs before the password comparison, so the lock cannot be
probed by timing. Now a 429, and the UI says to wait.

Worth noting how it was found: the first fix asserted 429 and still got 401, and rather than
reaching for a second guess, one log line printing `e.getClass().getName()` named the wrapper
immediately. The wrapping is not visible at the call site and is not what the code appears to say.

**And the account he was locked out of was not his.** It had been created by the browser suite,
which hardcoded his real email address — see the `.invalid` change below.

### The test suite that took the app hostage (2026-08-28)

The Playwright suite created its account under Allen's real email address and left it there. Two
consequences, both bad:

* Allen found a sign-in screen for an account he had no memory of creating, with a passphrase that
  existed only in a spec file. I initially told him he must have created it, which was wrong.
* Because an account existed, the app stopped offering first-run setup — so a *passing* test run
  locked the owner out of his own application.

Suite-owned data now lives at `e2e@finances.invalid` (RFC 2606 reserves `.invalid` precisely so it
can never be real), the constants are shared with the reset guard so the two cannot drift, and a
global teardown clears up after the run. The teardown re-runs the same "does this look real?" guard
first, so it can never delete anything a test did not create — and if the database has grown real
data, it warns and leaves it alone rather than failing the run.

### `make test` did not work on a clean shell (2026-08-28)

Every suite had been passing all week — with `JAVA_HOME` and a newer Node exported by hand in my
shell, which is not a thing the repo knows about. From a fresh terminal `make test` died on
*"Unable to locate a Java Runtime"*: Homebrew's `openjdk@25` is not registered with macOS's
`java_home` unless it has been symlinked into `/Library/Java/JavaVirtualMachines`. Angular 22
likewise needs a newer Node than nvm's default here, and its failure reads like a broken test run
rather than a wrong shell. The `Makefile` now resolves both — `java_home`, then the Homebrew prefix;
`nvm use` from `.nvmrc` when nvm is present — and says what to install if it finds nothing.

A green suite is only evidence if it is green for someone who has not been configuring their shell
for a week.

### "Invalid Date" — the first thing a new user sees (2026-08-28)

Allen signed in for the first time and the dashboard greeted him with **Invalid Date**.

The spending card took its month label from the data: `monthLabel(thisMonth()[0]?.month ?? '')`.
A brand new account has no rows, so that is `monthLabel('')`. The author saw the risk and wrote
`|| 'This month'` after it — which never fires, because `toLocaleDateString` on an unparseable date
does not throw and does not return empty. It returns the **string** `"Invalid Date"`, which is
truthy. The `?? ''` and the `|| fallback` were both dead code, guarding against a null that never
comes while the real failure walked straight past them.

Two fixes, because either alone would leave the trap armed:

* `monthLabel` returns `''` for anything it cannot parse, so no caller can render that text.
  `budget.html` had the identical latent bug and is now covered by the same change.
* The dashboard subtitle uses the month it actually displays — the card always shows the current
  month — instead of reading it off whichever row happened to arrive first. The label is knowable
  without any data, so deriving it from data was the mistake underneath the mistake.

The lesson worth keeping: **a fallback is only a fallback if you know what the failure actually
returns.** Guarding with `||` assumed a falsy failure value. One line in a REPL would have shown
otherwise.

Pinned twice — `money.spec.ts` asserts the empty return across seven malformed inputs, and the
browser suite asserts no dashboard reachable by a new account contains that string. The browser
test was verified by reintroducing the bug and watching it fail (`Expected: 0, Received: 1`).

### The tests and the app stopped being able to share a database (2026-08-28)

The browser suite truncated the dev database before every run, mitigated by a guard that refused if
the data "looked real". The moment Allen created a genuine account, that guard did its job and the
entire suite became unrunnable — correctly, but it meant *no browser testing at all* for as long as
he used the app.

Scoping the deletes per-user was tempting and would not have worked: `/api/v1/setup` only creates a
user on a **first run**, so with a real account present the suite cannot create its own through the
UI at all.

The suite now gets its own stack — a separate compose **project** (`finances-e2e`) built from the
same `docker-compose.yml`, so Docker namespaces the containers, network and volume away from the
dev stack for free. No second compose file, no duplicated YAML; only the published ports differ
(4201/8081). `make e2e` brings it up and runs against it, `make e2e-down` disposes of it.

The safety property is now structural rather than procedural: Playwright's defaults point at the
e2e stack, so a bare `npx playwright test` typed by hand cannot reach the dev database — it is not
pointed at it. If the test stack is not running, the suite fails saying so, which is a far better
outcome than truncating a database somebody is using. The "does this look real" guard stays as a
second line of defence, because the check costs one query and being wrong costs real statements.

## M3 — Categorization
The three tiers from D-15: rules, then similarity against his own history, then LLM fallback.
Corrections captured and fed back. Accuracy measured against a held-out set of his own transactions,
not vibes.

**Groundwork already in place (2026-09-24), and two rules for whoever wires it:**

* The rules engine returns `method="none"` when no tier had anything to say. **That is a signal to
  escalate, never a row**: `categorization.method` admits `rule`, `similarity` and `model` by CHECK,
  and a "suggestion" of nothing would sit in the review queue as if a rule had decided. The
  persistence path must skip `none`.
* `transaction.source_type` (V9) keeps the file's own word for each row — Chase's `Payment`,
  OFX's `XFER`, a brokerage's action. It is what the transfer and refund hints were read from, the
  review queue shows it ("file says Return"), and it is a free tier-1 feature for the categorizer:
  the institution's classification, not an inference over the description.
* The baseline to beat is the real month recorded under M2: 14 of 40 categorized by rules, 9
  correctly excluded as transfers, 17 to review. Measure against that, on a synthetic twin of that
  file, before and after each tier.

**Done when:** a month of new transactions comes in and the majority are correctly categorized
without intervention, and the number is *measured*.

## M4 — Multi-entity + investments

**Started 2026-08-27, from a real Fidelity export.** The parser is built and verified against
Allen's actual `Portfolio_Positions` file — 29 holdings across 8 accounts, no warnings. Persisting
them still needs the `security` / `holding` schema.

**A positions export is not a transaction history.** It is a snapshot: quantity, price, market value
and cost basis at a moment. It contains no money movements, so it cannot feed the ledger and does
not advance M2. Fidelity's *Accounts History* export is the transaction one.

Quirks the parser handles, every one found in the real file rather than imagined:

* A **UTF-8 BOM** on the header, so the first column reads `\ufeffAccount number` and every row
  silently loses its account.
* **Legal prose after the data** — quoted paragraphs and a `Date downloaded ...` line. Parsed as
  rows by any naive reader. That line is also the only "as of" the file carries, so it is read
  rather than discarded.
* **Footnote markers glued to tickers**: `SPAXX**`, `USD***`.
* **A `Type` column that does not mean what it looks like.** It is the account's *registration*
  (Cash or Margin), not whether the row is cash — reading it as the latter classified **AAPL as a
  cash holding**. Cash is decided by symbol instead. This one is invisible without a real file.
* **Cash rows with a value but no quantity, price or cost basis**, and `--` for not-applicable,
  which is not the same as zero.
* **Several accounts in one file**, so the importer cannot assume a single nominated account.

**Security:** the parser never returns a full account number — only the last four plus a one-way
`account_key` that lets re-imports match the same account. An early version leaked the full number
through the `raw` passthrough, which defeated the masking beside it; that is now filtered and
tested. Real exports are gitignored, and every fixture in the repo is synthesized.

### Built 2026-08-29: securities, holdings, and the balance rule they change

`V5__securities_and_holdings.sql` adds `security` and `holding`, and rewrites `v_account_balance`.
That rewrite is the substance; the tables are the easy part.

**A brokerage is worth its holdings, not the cash paid into it.** The balance view summed
transactions for every account. For a brokerage that is the money that went *in* — deposit $10,000
over three years, watch it become $14,000, and the ledger still says $10,000. Understated by every
dollar of growth, silently, with a figure that looks entirely reasonable. So an account with a
holdings snapshot now takes its balance from the snapshot; everything else still sums transactions.
`v_net_worth` inherits it with no case of its own, which is the payoff of keeping the rule in one
view. `HoldingsTest.brokerageValueComesFromHoldingsNotDeposits` is the test that matters here.

Switching sources drops nothing, because Fidelity lists money-market and cash rows (SPAXX, USD) as
holdings — uninvested cash is inside the snapshot. Verified end to end: a fixture across three
accounts imported to the right two, refused to guess the third, and produced a market value that
included the cash sweep.

**A snapshot has a date, and the view says so.** `balance_source` and `balance_as_of` are exposed
rather than hidden. A market value is only as current as the last file imported, and a stale figure
whose date you can see is worth far more than a fresh-looking one that is wrong.

Two smaller decisions worth keeping:

* **`quantity` is `NUMERIC(28,8)`, not `(19,4)`.** A share count is not money. Funds settle to three
  decimals and crypto to eight; rounding to cents would change what someone owns.
* **The snapshot key is (account, security, as_of).** Re-importing updates in place, while a file
  downloaded later lands beside the old one. Keeping both is the only way a position acquires a
  history — a positions file contains none.

*Found while building:* `/api/v1/holdings` answered 500 on its first call. `open-in-view` is off, so
the lazy `security` association threw when the response was built outside the transaction. Fetch
joins fixed it and removed an N+1 at the same time. Worth noting the unit tests were green — it took
calling the endpoint.

### Still to build for M4
Personal vs. Feeling Froggy LLC separation, and business expense flagging with an eye toward tax
time. Holdings are not yet shown in the UI; they are reachable over the API and through the MCP
server (`list_holdings`, `import_positions`).

## M5 — Aggregator connection
Whichever vendor wins D-14, behind the `AccountConnector` port built in M2. Automatic sync,
credential handling per `docs/SECURITY.md`, reconciliation against imported statements.

## M7 — Market data and trade support

**M7a built 2026-09-26 (D-18).** Watching, not trading yet. `watchlist`, `quote`, `price_alert` and
`alert_event` (V10); the Python service's market-data provider with Alpaca behind it and a labelled
fake for tests; the API's five-minute poll, its alert edge detector and ntfy delivery; a Markets
screen with the watchlist, alerts, their history and every holding valued at the latest quote
beside its snapshot value; MCP tools for all of it. The rule that governs the whole milestone is
in the schema comment: a price is not money. Nothing here enters the ledger or moves a balance.

**M7b built 2026-09-26.** `trade_order` and `trade_order_event` (V11): draft → confirmed →
submitted → accepted → filled, with cancelled, rejected, expired, failed and placed_manually as the
ways out, and every transition an event with an actor. The confirmation is an echo of the draft;
then the kill switch (`TRADING_ENABLED`, off by default), then the daily notional cap
(`TRADING_DAILY_CAP`), then the broker — Alpaca's paper endpoint through the Python service, and
only that endpoint. A manual ticket stops at confirmed, goes to Fidelity in the person's hands, and
is marked placed with its fill. The orders card on the Markets screen and seven MCP tools, whose
`confirm_order` is marked destructive so the client asks first. Nothing here is a ledger row.

**M7c built 2026-09-26 (D-19).** Strategies as pure functions over bars in the Python service —
buy and hold, moving-average crossover, RSI mean reversion, opening-range breakout — and a
backtester built to be hard on itself: next-bar fills, slippage and commission, whole shares, buy
and hold beside every result, an out-of-sample tail, same-session round trips counted, and a list
of warnings the person reads before the numbers. `strategy` and `backtest_run` (V12) keep the
rules and the claims whole; `trade_order.strategy_id` says which rule proposed an order. A live
strategy is asked on a timer and turns a new signal into a *draft* through the M7b pipeline, with
a notification; it never sends. A Strategies screen (form from the catalog, the result with its
doubts first, an equity curve, the in-sample/out-of-sample halves, past runs, saved strategies
with a live switch) and nine MCP tools.

**M7d, not started:** auto-execution within limits, once the paper record earns it (D-18); a
funded account as a configuration change; more rules (breakout with volume, an earnings-date
filter), short entries, per-strategy risk limits.

*The original sketch, kept for what it got right:*

Raised by Allen 2026-08-24, explicitly as a future possibility rather than scope. Recorded so the
earlier milestones do not accidentally design it out.

The shape: a section that watches the market, and eventually supports making more precise trades —
possibly through this app, possibly in combination with something else. Fidelity's Active Trader
Pro is the "beyond the base app" surface Allen has in mind.

What this means for the work already done:

* **Nothing needs building now.** M1–M6 are unaffected.
* **`security` / `holding` / `position` (M4) are the foundation**, not a separate track. Quotes and
  trades hang off instruments and positions; get those right and market data is additive.
* **`services/ai` is probably the right home for a market-data feed**, not the Java API — it already
  owns outbound network access to external endpoints (docs/ARCHITECTURE.md), and streaming quotes
  are a polling/socket workload rather than a system-of-record concern. No new sidecar needed.
* **Order execution is a different risk class from everything else here.** Reading balances wrong
  shows a bad number; sending an order wrong loses money irreversibly. If this is ever built it
  needs its own decision record covering authorization, confirmation, rate limits, and a kill
  switch — do not let it inherit the trust model of the read-only ledger.
* **Market values are prices, not money movements.** They do not belong in `transaction`, and the
  sign convention does not apply to them.

## M8 — The app speaks up (D-20), built 2026-09-26

`reminder`, `notice_preference` and `digest_run` (V13). One list of what needs a look — mismatched
statements, orders waiting or refused, reminders coming due, categories over target this month,
accounts nobody has imported for a while, undelivered alerts, strategies that could not be
evaluated — composed from what the system already knows, worst first, as sentences with the number
in them and a screen to go to. The dashboard's "Needs a look" card, the MCP `needs_a_look` tool and
the daily ntfy push all read that one list. The push goes once a day at the person's chosen time,
stays quiet on a quiet day, and every run is recorded with what it said and whether it got
through. Reminders live beside the list: once or on a cadence, with a lead time, done advancing a
recurring one. Eight MCP tools.

**Next for M8:** recurring charges detected from history (subscriptions, bills, paydays) feeding
both the list and a cash-flow forecast; anomaly flags (duplicate charges, fees, a transaction far
outside a merchant's usual size); a nightly net-worth snapshot so trends exist.

## M6 — Insight layer
Forecasting, anomaly detection ("this bill is 40% higher than usual"), cashflow projection, and a
natural-language query surface over the ledger. This is the part that needed a decade of fintech
to become reasonable, and it's now the easy part — but only if M2 and M3 produced clean data.

---

**The order matters.** Every interesting thing in M6 depends on trustworthy categorized data from
M3, which depends on reliable ingestion in M2, which depends on a sound model from M1. Resist
building M6 early on synthetic data; it will teach you the wrong things.

## M2.5 — Conversational control (D-17), 2026-08-28

`services/mcp` exposes the API to Claude Code over MCP. Reads, writes, imports, deletes and undo —
see D-17 for the reasoning and `services/mcp/README.md` for setup.

### Three bugs that only running it could have found

The unit tests passed throughout. Each of these needed the thing actually wired up and called.

**The token authenticated nothing.** The filter guarded on
`SecurityContextHolder.getContext().getAuthentication() == null`, which reads as "nobody has
authenticated yet" and never is: `AnonymousAuthenticationFilter` populates the context with an
`AnonymousAuthenticationToken` for every unauthenticated request. The guard silently never passed
and every request came back 401. Fixed with an `AuthenticationTrustResolver`, which is what tells
"nobody" from "somebody" — and which also means a genuine session in flight is never overwritten.

**Every controller 4xx came back as 401.** Restoring an already-restored transaction answered 401
instead of 404. Spring re-dispatches errors through the filter chain, and `OncePerRequestFilter`
opts out of that pass by default, so the security context had been cleared and the authorization
filter saw an anonymous request to `/error`. The effect was that *any* refusal — unknown account,
bad date, missing row — told the caller their token was wrong, sending them to check the one thing
that was never the problem. Fixed by overriding `shouldNotFilterErrorDispatch()`.

**Docker made the loopback check unenforceable.** The token worked natively and 401'd in the
container. Docker NATs published traffic, so a request the host sent to `127.0.0.1:8080` arrives
with the bridge gateway as its source and the loopback check refuses a caller that genuinely is
local. Worth noting the first version of the compose comment asserted the opposite — reasoning
about NAT from memory rather than observing it. The check is off in Docker now, where the port
being published to `127.0.0.1` provides the same boundary, and left on natively where it works.
The filter logs this case specifically: the symptom is a 401 identical to a wrong token, and the
token is the first thing anyone re-checks.

A fourth, milder one from the same session: `list_transactions` returned Spring's `Page` envelope
while `list_deleted_transactions` returned a bare array. Iterating the first yields *field names*
rather than transactions, and nothing raises — the caller just quietly gets nonsense. Every listing
tool now returns the same shape with `has_more` stated rather than inferred.

### What made these findable

A single smoke script that called every tool against a real stack, in the order a person would:
create, record, categorize, transfer, report, delete, list, restore, then deliberately do each
thing wrong. The failures were all in the seams — filter ordering, error dispatch, container
networking, response envelopes — which is exactly where hermetic tests do not look.

It ran against the **scratch stack**, not the dev database. That is what the isolated e2e project
bought: real end-to-end verification against real fixtures, with Allen's data untouched throughout.
