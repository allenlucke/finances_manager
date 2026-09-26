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
