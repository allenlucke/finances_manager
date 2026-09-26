# Decisions

Every decision below is **SETTLED**. D-01 through D-09 were pinned in the M0 scaffold; D-10 through
D-16 were open placeholders and were resolved on 2026-08-22. None should move without a reason —
and several now have downstream consequences recorded against them, so re-opening one means
re-checking what depends on it.

All versions verified current-stable as of **August 2026**.

---

## SETTLED — pinned in the M0 scaffold

### D-01 — Rebuild in place, archive the old code
The 2021 tree moved to `legacy/` on branch `modernize-2026`. Git history (121 commits) and the
GitHub URL are preserved. Tag `legacy-2021` marks the last commit of the old app.
*Why:* history is worth keeping, the domain model is worth reading, and nobody wants a second repo.

### D-02 — Java 25 (LTS)
Released September 2025, LTS through 2033. Spring Boot 4.1 supports Java 17–26; 25 is the newest
LTS inside that window. Java 26 exists but is a 6-month release with no long-term support.

### D-03 — Spring Boot 4.1.x
Current generation (4.1 released June 2026, OSS support through July 2027). Baseline Spring
Framework 7. This is a big jump from the legacy 2.4.8 — `javax.*` → `jakarta.*`, and the old Jersey
setup goes away in favor of Spring MVC.

### D-04 — PostgreSQL 18
Same database engine the project already used, several major versions on. The legacy schema and
stored procedures port cleanly.

### D-05 — Angular 22
Current stable (June 2026) and the active LTS through mid-2027. Modern Angular is a different
language than the Angular 12 in `legacy/`: standalone components, signals, the new control flow
(`@if` / `@for`), `inject()` over constructor injection. Do not copy legacy component patterns.

### D-06 — Three services, one database
`api` (Java) owns Postgres and all business rules. `ai` (Python) is stateless and does document
parsing and inference. `web` (Angular) talks only to `api`.
*Why not one Java monolith:* the ML/LLM ecosystem is Python, and the legacy repo already had a
Django "reader" app parsing Chase CSVs — that instinct was right.
*Why not microservices:* one person, one database, no distributed transactions. Two backends is
the ceiling.

### D-07 — Flyway for migrations, applied by the API service
Versioned SQL in `services/api/src/main/resources/db/migration/`, on the classpath so the app
carries its own schema. Forward-only — never edit an applied migration.

### D-08 — Money as `NUMERIC(19,4)` / `BigDecimal`
Widened from the legacy `NUMERIC(12,2)` to survive share quantities, FX, and interest math.

### D-09 — Statement import is milestone 1 of data ingestion
Before any aggregator contract, the system reads files the banks already give away: CSV, OFX/QFX,
and PDF. Zero cost, no vendor approval, works on day one, and doubles as the training/eval corpus
for categorization. Aggregators layer on top of the same normalized transaction model.

---

## SETTLED — resolved 2026-08-22 (previously OPEN)

D-10 through D-16 were open placeholders in the M0 scaffold. All seven were discussed and
decided in the session of 2026-08-22. The original option analysis is preserved in git history
(see the commit that closed them) — what follows is the decision and the reason it won.

### D-10 — Build tool: Maven
`services/api` stays on Maven, and `mvnw` is now generated and committed (Maven 3.9.16).

*Why:* one module, no custom build logic, no codegen, and a 4-second test cycle — none of the
conditions where Gradle's incremental builds pay off. Maven is the format Spring Boot's own
documentation assumes, which matters on a project that goes dormant. jOOQ's build-time codegen
was the one real argument for Gradle and D-11 removed it.

*Note:* the Makefile prefers `./mvnw` when present, so `make api` and `make test-api` are now
version-pinned rather than depending on whatever `mvn` is on `PATH`.

### D-11 — Persistence: JPA / Hibernate
Spring Data JPA for the domain. Reporting queries live in **SQL views created by Flyway
migrations**, read through Spring Data projections — not `@Query` strings scattered through
repository interfaces.

*Why:* JPA is the best-known option with the strongest tooling support, and Spring Data gives
CRUD for free across a ~12-table domain.

*Known cost, accepted:* M1 includes the balance-sheet reporting ported from
`legacy/db/budgetBalanceSheet.sql` and `acctBalScratch.sql`. Those are multi-table joins with
aggregation and date-range correlation; Hibernate will not generate them. Native SQL in M1 is
expected, not a surprise — putting it in migration-created views keeps it version-controlled and
reviewable alongside the schema.

*Guardrails, already set in `application.yml` and not to be changed:* `open-in-view: false` and
`ddl-auto: validate`. Flyway owns the schema; Hibernate never modifies it.

### D-12 — Auth: self-hosted, session cookies + passkeys
Spring Security 7 in the API service. **Server-side session cookies** (`Secure`, `HttpOnly`,
`SameSite`), **not** self-issued JWTs. WebAuthn passkeys as the second factor.

*Why not JWT:* JWTs buy stateless horizontal scale and cross-service auth. This is one API
process and one SPA on the same origin, so the benefit never arrives — but the cost does: a JWT
cannot be revoked, and a revocation blocklist just reinvents sessions badly. For a system holding
aggregator tokens, "kill every session now" is a requirement.

*Why not Keycloak or a hosted IdP:* Keycloak is a container, a datastore, and a permanent upgrade
obligation for 1–3 users. A hosted IdP puts a third party in the login path of your own money.

*Why self-hosted MFA is no longer the trap this file warned about:* Spring Security 7.1 ships
first-party `WebAuthnConfigurer`, `WebAuthnDsl`, `EnableMfaFiltersConfiguration`, and
`WhenWebAuthnRegisteredMfaConfiguration`. Passkeys are enabled, not assembled — and they are
phishing-resistant, unlike the TOTP this file assumed you would hand-roll.

*Follow-up, done in M1a:* the scaffold's `SecurityConfig` disabled CSRF, which was correct for a
bearer-token scaffold and wrong for cookies. It is on: `CookieCsrfTokenRepository` with the SPA
echoing `XSRF-TOKEN` as a header, exempting only local-token requests. `ApiFlowTest.csrfIsEnforced`
pins it, and sign-out re-primes the token (CLAUDE.md).

### D-13 — Frontend styling: Angular Material, alone
No Tailwind, no utility framework layered on top, no second styling system.

*Why:* a budgeting app is data tables, date pickers, and validated form fields — Material's
strongest surface, and accessible without hand-rolling ARIA on a data grid. It is version-locked
to Angular and updated by the same team, which matters on a repo that just came off a four-year
dormancy. M2/M3 bring thousands of transactions per view, needing CDK virtual scroll and sticky
headers — Material is built on the CDK, so that arrives in the same system.

*"Alone" is part of the decision.* The legacy app carried Material + Bootstrap 5 + jQuery + popper.
Material's look is retunable via its token-based theming since v17; CSS Grid and flexbox handle
layout without a utility framework.

### D-14 — Aggregator: no vendor yet, but an aggregator-ready schema now
No vendor commitment. `AccountConnector` is the name of the port — file import is its first
implementation, and the Java interface itself arrives with the second, at M5, when there is
something to abstract over. **What changed: this is no longer fully
deferred.** The M2 schema must absorb aggregator semantics up front, or M5 becomes a migration
instead of an adapter.

Required in the M2 `transaction` / `connection` design:

* **Pending-transaction linkage.** Aggregators return pending rows that later vanish and are
  replaced by a posted row with a different ID, a different post date, and sometimes a different
  amount (tips, fuel holds). The dedupe key `(account, date, amount, normalized description)` will
  happily double-count that transition. Plaid supplies `pending_transaction_id` — there must be a
  column to hold it. This is the same class of bug as the credit-card double-count in DOMAIN.md.
* **Provenance** — which connection or import batch produced each row, so M5 can reconcile
  aggregator data against previously imported statements.
* **ISO currency code** — do not hardcode USD.
* **Provider category hints** — a free tier-1 signal for M3.

*Vendor notes for when M5 arrives:* Plaid is strongest on US checking/savings; SnapTrade is
generally better on brokerage positions and trade data. Covering both banks and Fidelity may
require two connectors, which is a further argument for the port. Plaid production access needs an
application and has historically been unfriendly to individual developers — plan for file import
remaining the primary ingestion path indefinitely (D-09 already makes that case).

### D-15 — Python service: FastAPI, local embeddings, hosted Claude for tier 3
**Framework: FastAPI**, unchanged. Django would drag in an ORM that must then be kept away from
Postgres, fighting the "only `services/api` touches the database" rule. A queue worker is premature.

**Tier 1 (rules)** — built and working.

*Done in M2:* the Chase `Type` column is consumed — `Payment` marks a transfer, `Return` and
`Adjustment` a refund (two flags, because a refund must stay categorizable) — and the categorizer's
source-hint tier ranks it above any description pattern. The dead `looks_like_non_expense()` is gone.

**Tier 2 (similarity) — local, on CPU.** Anthropic has no embeddings endpoint, so "hosted" here
would mean a second vendor anyway. A personal ledger has hundreds of distinct merchants, not
millions, and merchant strings are short — a small local sentence-transformer plus brute-force
nearest-neighbour is instant. **Do not add pgvector or a vector database:** it is unnecessary at
this scale and would violate the single-writer database rule.

**Tier 3 (fallback) — `claude-opus-5` via the Batch API.** Only for rows tiers 1 and 2 could not
resolve. Four choices keep this cheap:

1. **Batch API** — categorization runs after import and is not latency-sensitive. 50% off.
2. **Many transactions per request** (~100), not one per call. Most input tokens are the taxonomy
   and instructions; batching amortizes them across the whole request rather than paying per row.
3. **Prompt caching** on the taxonomy and correction examples — a stable prefix.
4. **Structured outputs** (`output_config.format`) constrained to the category enum, so responses
   are valid categories rather than free text to parse and retry.

*Costs, measured against these choices:* a one-time 5,000-transaction historical backfill lands
around **$1–2**; steady state is a few dozen novel merchants a month, which is noise. Naively
calling once per transaction would cost ~10x that — the batching is what makes it cheap.

*Build in from the start:* persist every suggestion with its method and confidence to the
`categorization` table (DOMAIN.md), since that is both the tier-2 training signal and the only way
M3 can measure accuracy rather than guess. Cap tier-3 calls per import batch so a malformed file
cannot become a surprise bill.

*Not building:* an Ollama implementation or a provider abstraction. Local inference was considered
and rejected on cost grounds — the bill is small enough that a weaker model is not worth a larger
human review queue. If that changes, it is a small module added later.

*Secrets:* `ANTHROPIC_API_KEY` lives in `.env` (gitignored, already stubbed in `.env.example`).
The AI service holds it; it has no database credentials. See SECURITY.md.

### D-14a — Feeling Froggy is an independent app; this one reacts to it
*Raised 2026-08-27. **Corrected the same day** — the first version of this entry was wrong.*

**Allen's decision:** Feeling Froggy is its own application. It does its own categorization and owns
its own books. This system consumes what FF has already decided; it does not re-derive it.

The entry originally recorded here argued the opposite — that because FF banks at the same credit
union, its raw export could simply be imported against the Feeling Froggy `ledger_entity` and no
integration was needed. That reasoning was sound on effort and wrong on architecture. Two systems
categorizing the same transactions independently will disagree, and then the interesting question
stops being "what did I spend" and becomes "which app is right". Cheapest is not the same as
correct when it creates a second source of truth.

What this means concretely:

* **FF sends categorized results**, not raw rows. The unit of exchange is a decision FF has already
  made, so there is nothing here to re-classify.
* **This system may confirm, reconcile and report** — comparing FF's figures against a bank export
  is legitimate and useful. Overriding them is not.
* **The two apps stay decoupled.** They agree on a payload shape, not a database, not a library.
  What this side owes FF is *example data* — a documented contract with realistic samples — so FF
  can build against it without depending on anything here.
* **The transport is an API or MCP surface at M5**, as D-14 always had it. This entry does not
  change that; it changes what travels over it and why.

Raw import against the FF entity still works and is a perfectly good stopgap while FF is being
built. It is a convenience, not the design.

### D-16 — Deployment: homelab box, Tailscale-only
An always-on machine Allen owns, reachable only over a Tailscale mesh VPN. **Never publicly
routable.** No managed cloud — at one user, autoscaling and multi-AZ are cost and complexity that
never activate.

*Why VPN-only is the load-bearing part:* this app has one user and no reason to have a public IP.
Removing public reachability eliminates credential stuffing, login-page attacks, and exposure to a
Spring Security CVE on a system holding bank tokens.

*Effect on M1:* SECURITY.md sets "MFA before this app ever faces the public internet with real data
in it" as a hard gate. Behind Tailscale that gate does not bind, so passkeys are built deliberately
as defense-in-depth rather than as a blocker on shipping M1. **This is contingent on staying
VPN-only** — exposing it publicly later re-arms the gate.

*Correction to this file's original framing:* D-16 was described as determining backup strategy.
It does not. The backup requirement is identical on every target — encrypted, off-site, automated,
and **restore-tested** — and `pg_dump` to encrypted object storage satisfies it anywhere. Backup
work therefore does not wait on hosting, and must precede the first real data. An untested backup
is not a backup, and years of categorization corrections cannot be reconstructed from a bank
statement.

*Accepted cost:* Allen owns uptime, power, and hardware failure. Acceptable for a tool checked a
few times a week; if it becomes daily-critical, a VPS with the same Tailscale-only architecture is
the drop-in alternative.

### D-17 — Conversational control via MCP and Claude Code, not an in-app chat box
*Decided 2026-08-28 with Allen.*

**Allen's decision:** he wants to type instructions to this system in natural language, and he wants
to use his Claude Max subscription rather than pay API fees. He chose **full control** — the
conversational layer may do anything the UI can, including deleting.

**What we built:** `services/mcp`, an MCP server over the existing REST API, registered in
`.mcp.json`. The chat interface is Claude Code itself.

*Why not an in-app chat box:* it would need `ANTHROPIC_API_KEY` and cost per message. More
importantly, a Max subscription covers Allen's own interactive use through Anthropic's clients — it
is not a backend credential, and wiring an application's inference through Claude Code to avoid API
fees is both outside what the subscription is for and mechanically fragile. Driving the app *from*
Claude Code is the same thing he wanted, is what the subscription is actually for, and costs
nothing. He already has Superwhisper pointed at that window, so it answers the voice request he
withdrew as well.

*This does not reopen D-15.* That decision covers **bulk categorization** — rules, then local
similarity, then a Claude batch fallback at roughly $1–2 for a full historical backfill. That work
is genuinely programmatic and still needs the API. The two are different problems: one is Allen
asking a question, the other is five thousand rows being classified with nobody watching.

*Why full control, and what makes it safe:* Allen was told the risk and chose it. Rather than
narrow the capability, the answer is recoverability — deletion here is soft, and M1 had no way to
undo one, so `POST /transactions/{id}/restore` and `GET /transactions/deleted` were added. Restoring
brings back every leg the same delete removed, or a transfer would come back half-formed.
`delete_transaction` returns the exact call that undoes it. A delete you cannot perform is not safer
than a delete you can undo.

*Authentication:* a bearer token (`LOCAL_API_TOKEN`) that authenticates as the sole account owner.
The design constraints, all tested:

* **Off unless set.** Blank disables the filter outright, so the bypass does not exist by default
  and an empty configured token cannot match an empty presented one.
* **Loopback only**, checked before the token is compared — so a remote caller cannot time it. It
  reads `getRemoteAddr()` and never `X-Forwarded-For`, which is caller-supplied.
* **Constant-time comparison**, so a wrong token leaks no prefix.
* **Minimum 32 characters**, enforced at startup. A guessable bypass is worse than none because it
  looks like protection.

*The concession, recorded rather than buried:* the token asserts the WebAuthn factor, so registering
a passkey no longer covers a caller holding it. Acceptable only while loopback-scoped and VPN-only
(D-16). Revoking it is one line in `.env` and a restart.

*Known limitation:* in Docker the API cannot enforce loopback itself — Docker rewrites the source
address of published traffic, so the check sees the bridge gateway and refuses a caller that really
is local. Compose disables the in-process check and relies on publishing the port to `127.0.0.1`
only, which is the same boundary the rest of the app already depends on. Running natively keeps the
real check, which is why it stays the default. The filter logs this specifically, because the
symptom is a 401 that looks exactly like a wrong token.

*Not built:* account and category deletion. Neither the UI nor the API has ever had it, and adding
it only for the agent would put the most consequential operation in the least supervised place.
Say the word and it is a small piece of work.

### D-18 — Watching the market, and what "trading" may mean here
*Decided 2026-09-26 with Allen, who took the recommendations as given.*

**Fidelity has no API for placing orders.** That is the fact everything else follows from. Reading
Fidelity positions programmatically is possible through an aggregator (SnapTrade, D-14); placing
orders there is not, and no amount of architecture changes it.

**Market data: Alpaca, through the Python service.** A free Alpaca account gives the IEX feed and a
paper-trading account in one signup, which is the whole of what M7 needs to prove itself. The vendor
key lives in `services/ai` — the one outbound connection that container makes, next to the
inference key it will hold later — and the Java API asks it over the compose network, so the rule
that only the API writes to Postgres and the rule that the AI service holds no database credentials
both stand. `MARKET_DATA_PROVIDER=none` is the default: the screen says market data is off rather
than the service inventing anything. A `fake` provider exists for tests and a vendorless stack, and
says so on every quote it produces.

**A price is not money.** Quotes have no direction, never enter the ledger, and never change a
balance. A holding valued at the latest quote is shown *beside* the value its positions snapshot
gave it, labelled as a moment; `v_account_balance` and net worth keep using the snapshot, which is
what the broker asserted. Reversing that would turn net worth into a number that moves every five
minutes on a fraction of IEX volume.

**Egress.** The homelab box is Tailscale-only *inbound*. Outbound to the vendor and to the
notification channel is allowed and named in SECURITY.md; nothing else may open a connection out.

**Alerts: ntfy.** A plain HTTP push service with a phone app, self-hostable on the box and reached
over Tailscale, or the public server with a long random topic. One URL configures it. An alert fires
once when its condition becomes true and re-arms when it is false again — an edge detector, not a
threshold check — and a percent-move alert re-arms each trading day. Every firing is recorded
whether or not it was delivered, with the reason when it was not.

**Trading, when it comes (M7b): propose, confirm, execute, with a ceiling and an off switch.** The
assistant may draft an order. A person confirms it, echoing the quantity and price back, before
anything reaches a broker. A daily notional cap and `TRADING_ENABLED=false` by default bound the
damage a mistake can do. Paper trading first, on Alpaca; a funded account is a configuration change
that earns itself with a track record. Fidelity stays manual: the app prepares the ticket, Allen
clicks. Auto-execution within limits is not ruled out; it is not granted until the paper record says
it should be. Order execution is a different risk class from everything else in this system —
reading a balance wrong shows a bad number, sending an order wrong loses money irreversibly — and
that is why it has its own record here rather than inheriting the ledger's trust model.

**Built (M7b, 2026-09-26).** `trade_order` and `trade_order_event` (V11). The confirmation is an
echo of the draft — symbol, side, quantity and limit must match exactly, and in the browser the
person types the symbol back — and after it come the switch and the cap, each refusing with a
sentence. A refusal is an outcome, not a fault: the order's state after it (confirmed but not sent,
cancelled by the cap, failed at the broker) is kept, so `OrderService.confirm` does not roll back on
a domain refusal. The cap counts what reached the broker today, sized at the limit price or the
quote the draft was made against; a confirmed-but-unsent order may be confirmed again once the
switch is on. Our own `client_order_id` travels to the broker, so a retry cannot place an order
twice. The Python service knows `alpaca_paper` and refuses any base URL that is not the paper host;
`alpaca` and `alpaca_live` are not configuration values. The MCP `confirm_order` tool carries the
destructive hint so the client asks before calling it, and its description says the person confirms,
not the assistant, unless told to in so many words.

### D-19 — Strategies and backtests: a backtest is a claim, and a strategy proposes
*Decided 2026-09-26 with Allen ("go to town on all your ideas"), building on D-18.*

**Why build this at all.** Allen may want to day trade, and it will not be at Fidelity (D-18).
Before any money follows a rule, the rule should have to earn it in front of a system built to
disbelieve it. That is what this milestone is: an honest backtester, and a way to run a rule live
that can only *propose*.

**A backtest is a claim.** The backtester is built to be hard on itself, and every choice below is
a choice against flattering the strategy. A signal decided on one bar fills at the *next* bar's
open, never the close it saw. Every fill pays slippage and commission; zero slippage is allowed
and named as a lie. Positions are whole shares, all-in. Buy and hold over the same bars, with the
same costs, is computed every time and shown beside the strategy's return; a result screen or an
MCP answer that shows one without the other is wrong. The last part of the period is held out and
scored separately, so a rule tuned to the first part shows itself. Same-session round trips are
counted and the FINRA pattern day trader rule is named when there are any. Fewer than thirty
closed trades is called noise. Fake bars say so in the first line. All of this arrives as a list
of warnings the person is meant to read before the numbers, and the screen puts it there.

**A strategy is a pure function over bars,** in the Python service, with indicators computed once
per series. The same code runs in the backtester and against the newest bars live, so what was
tested is what runs. Four to start: buy and hold (the baseline), a moving-average crossover, RSI
mean reversion, and an opening-range breakout that is flat by every close — the day trader's
classic, and the one that exercises the intraday path. Money and indicators are Decimal; the
Sharpe ratio is the one float, a dimensionless statistic.

**Live, a strategy proposes.** An active strategy is asked for its opinion on a timer (one minute
by default, behind the market scheduler's switch). A new signal becomes a **draft** order through
the M7b pipeline — proposed by the assistant, the strategy's reason as the rationale, sized by the
bar the signal came from — and the person is told through ntfy. Confirming it is the person's
act, with the same echo, switch and cap as any other order. One draft at a time per strategy;
while one is pending the strategy waits, and a signal from a bar already proposed on is not
proposed again. The strategy learns it is long from its own filled orders, not from a guess. Auto-
execution within limits remains what D-18 said it was: not granted until the paper record says it
should be, and when it is, it will be a bounded change to this one step.

**What it is not.** Not tick data (the free feed is IEX volume), not a portfolio optimiser, not a
promise. Its most valuable output is expected to be "this does not work", said with numbers.

### D-20 — The app speaks up: one list, said once a day
*Decided 2026-09-26 with Allen, who asked whether there were reminders. There were not.*

**Until now the app only spoke when a price crossed a line.** Everything else it knew — a
statement the ledger disagreed with, an account nobody had imported for a month, a category over
its target, an order waiting on a decision — it knew silently, on a screen someone had to open.
"Complete financial awareness" cannot mean that.

**One list.** `DigestService.compose` produces the list of what needs a look, worst first, as
sentences with the number in them and a screen to go to. The dashboard card, the MCP tool and the
daily push all read that one list, so they cannot disagree, and a new kind of item is added in one
place. Items come only from what the system already knows; nothing is inferred, and nothing here
writes a ledger row.

**Said once a day.** The push goes through the same ntfy channel as price alerts, at a time the
person sets (07:30 in the app's zone by default), and only once per day unless asked. A quiet day
sends nothing by default. Every run is recorded with what it said and whether it got through,
like an alert firing, so "did it run" and "what did it say" have answers.

**Reminders are the person's half.** A dated thing they asked to be told about, once or on a
cadence, with a lead time; done on a recurring one moves it to its next occurrence, done early
included. Amounts are optional and, as everywhere, never floats.

**Thresholds are preferences, not constants.** How long before an account counts as stale, how
long a draft may wait — these are the person's patience, kept in `notice_preference`.

### D-21 — The ledger's rhythm: recurring charges found, not declared; net worth kept by day
*Decided 2026-09-26 with Allen, as the next step of "complete financial awareness".*

**Recurring charges are arithmetic over the ledger, and live in the API.** Rows are grouped by a
normalized merchant and direction; three or more at a steady interval make a series, with a
cadence read from the median gap and a typical amount from the median. Every series carries its
evidence — how many times, last seen, typical, whether the amount varies — and a status a person
can act on: upcoming, on track, or missing. This is statistics anyone can check against the
transactions, so it belongs beside the reporting views in `services/api`, not in the AI service;
that boundary is for parsing and for models. If a model ever improves on it, it will replace the
grouping step and nothing else.

**Nothing is declared and nothing is stored.** The report is computed on request from the last
four hundred days. A declared list of subscriptions goes stale the week it is written; a found
list is as current as the last import. The cost is that a merchant whose description changes
looks like a new series for a while, and that is shown rather than hidden.

**Anomalies are the same reading.** A second charge for the same amount at the same merchant
within two days is a possible duplicate (an exact same-day duplicate never reaches the ledger — the
dedupe rule refuses it first). A last amount half again the typical one is flagged. Both feed the
needs-a-look list (D-20) with the numbers in them.

**Net worth is kept by day.** `v_net_worth` is always "now"; `net_worth_snapshot` is what it said
on each date, taken once a day by the housekeeping tick and on request, combined and per set of
books. History, never a balance: the ledger remains the source, and the snapshot table is never
read for a current figure.

### D-22 — Categorization, wired: history first, rules second, every decision kept
*Decided 2026-09-26 with Allen. D-15 named the tiers; this is how the first two run.*

**The person's own history is the first tier, and it lives in the API.** Earlier rows from the
same merchant (the recurring detector's key, D-21) that were categorized, and how consistently,
are the best evidence there is, and they are this database's, not the AI service's. Two agreeing
rows are the minimum; the confidence climbs with the count and falls with disagreement. The rules
tier in the Python service answers by category *name*; the API maps the name to the person's
categories. The higher confidence wins.

**Ninety percent applies; less waits with its reason.** A suggestion at or above 0.90 naming a
category the person has is applied without waiting, and recorded as `applied` — a fourth
resolution, distinct from a person's `accepted`, because the accuracy figure must be able to tell
them apart. Anything lower waits in the review queue with its confidence, tier and rationale. A
rule naming a category the person does not have waits by name, and the screen offers to create
it; nothing is invented behind the person's back.

**Every decision resolves its suggestion.** Choosing the suggested category accepts it; choosing
another corrects it, with the target kept; choosing none rejects it. That record is the training
set for the third tier and the denominator of the only accuracy figure this system reports:
(applied + accepted) over everything judged. Reported with its count, never alone.

**Two rules kept from the groundwork.** A suggestion of nothing (`method="none"`) is never a
row. A suggestion that says "transfer" is ignored here: only the file's own word marks a transfer.

**Not yet.** The third tier (a model, local or hosted) and the measured baseline on a synthetic
twin of the real month; the roadmap's "done when" still stands.
