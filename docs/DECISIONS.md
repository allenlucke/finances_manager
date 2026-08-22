# Decisions

Two kinds of entry below. **SETTLED** items are pinned in the scaffold and shouldn't move without a
reason. **OPEN** items have a provisional default so the scaffold builds, but Allen explicitly wants
to discuss them — treat the current choice as a placeholder, not a commitment.

All versions verified current-stable as of **August 2026**.

---

## SETTLED

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

## OPEN — discuss before building

### D-10 — Build tool for the API: Maven or Gradle? **(default: Maven)**
- **Maven** — what the legacy project used, one `pom.xml`, boring, Claude-friendly, universally
  documented. Slower builds, verbose XML.
- **Gradle (Kotlin DSL)** — faster incremental builds, better for a multi-module split later,
  nicer for custom build logic. More moving parts, more ways to break.

*Scaffold ships Maven.* Switching is a 20-minute job now and a painful one in six months.

### D-11 — Persistence: JPA/Hibernate, jOOQ, or Spring Data JDBC? **(default: JPA + Hibernate)**
This one matters more than it looks. The legacy app used raw JDBC with hand-written DAOs and
PostgreSQL stored procedures — you were doing SQL-first before it was fashionable.
- **JPA/Hibernate** — the default everyone knows, best Claude Code support, entity graphs for free.
  Fights back hard on reporting queries and complex aggregations, which is most of what a finance
  app *is*.
- **jOOQ** — typesafe SQL generated from the schema. Superb for balance sheets, running balances,
  window functions. Closest in spirit to what the legacy code was already doing. Commercial license
  only for non-open-source databases — free for PostgreSQL.
- **Spring Data JDBC** — simple aggregate mapping, no lazy-loading surprises, drop to SQL when
  needed. Less magic than JPA, less power than jOOQ.

*Recommendation to discuss:* JPA for CRUD, jOOQ or plain SQL views for reporting. Hybrid is normal
and works well, but decide it up front rather than drifting into it.

### D-12 — Auth **(default: self-issued JWT via Spring Security resource server)**
Legacy used `jjwt 0.9.1`, which is ancient and has known problems — none of it survives.
- **Self-issued JWT** — no vendor, no cost, fine for a handful of users. You own password reset,
  MFA, and lockout.
- **Keycloak (self-hosted)** — real OIDC, MFA, social login, no per-user cost. Another container.
- **Auth0 / Clerk / Cognito** — least work, free tier covers this easily, but it's an external
  dependency on the front door of your own money.

*Worth weighing:* this app will eventually hold bank tokens. MFA isn't optional forever, and
building MFA yourself is the part people regret.

### D-13 — Frontend styling **(default: Angular Material 22)**
Legacy used Angular Material *plus* Bootstrap 5 *plus* jQuery *plus* popper — pick one this time.
- **Angular Material** — first-party, accessible, data tables and date pickers included, which a
  finance app leans on hard. Opinionated look.
- **Tailwind + a headless kit** — total design control, current default in the wider ecosystem,
  more work to get accessible tables and pickers right.

jQuery does not come back under any circumstance.

### D-14 — Aggregator vendor **(default: none yet — abstract behind a port)**
You picked all three data sources, which is the right long-run answer, but they arrive in order.
- **Plaid** — broadest US bank coverage, `/investments` product covers brokerages including
  Fidelity holdings and transactions. Paid, and production access needs an application.
- **SnapTrade** — brokerage-focused, generally stronger for investment accounts and trade data,
  weaker on checking/savings.
- **MX / Finicity / Yodlee** — enterprise-oriented, heavier sales process.
- **Direct Fidelity API** — realistically not available for individual developers. Fidelity's
  access for retail customers is via aggregators or file export, not a public API. Plan around it.

*Recommendation:* build `AccountConnector` as an interface in the API service with a file-import
implementation first. The aggregator becomes one more implementation, and you can trial vendors
without touching the domain.

### D-15 — Python service framework and inference strategy **(default: FastAPI + a three-tier categorizer)**
- **Framework:** FastAPI (default), vs. Django (what `legacy/` started) or a plain worker with a
  queue. FastAPI is the lightest thing that gives typed request/response models.
- **Categorization strategy** — the interesting part. Suggested layering, cheapest first:
  1. **Deterministic rules** — merchant string → category. Handles 70–80% of a personal ledger
     after a few months of corrections, at zero cost and zero latency.
  2. **Embeddings + nearest neighbour** — match against the user's own past categorized
     transactions. This is the piece that actually learns *your* habits.
  3. **LLM fallback** — only for genuinely novel merchants, plus PDF statement extraction where
     layout parsing beats regex.
  Every correction Allen makes feeds tier 1 and 2. Sending every transaction to an LLM is both
  expensive and worse.
- **Where inference runs:** hosted API (Claude/OpenAI) vs. local models via Ollama. Local is
  attractive here specifically *because* the input is your complete financial history.

### D-16 — Deployment target **(default: undecided, Docker Compose only)**
The scaffold runs locally. Whether this ends up on a homelab box, a VPS, or a cloud provider
changes what CI should build. Not urgent — but it should be answered before the first real data
goes in, because it determines backup strategy.
