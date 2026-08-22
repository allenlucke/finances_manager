# CLAUDE.md — finances_manager

> **Read this first, then `docs/DECISIONS.md`.** Several stack choices in this scaffold are
> *provisional defaults*, deliberately flagged as open. Allen wants to talk them through with you
> before you build on top of them. Do not treat the scaffold as settled architecture.

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
│   └── ai/                ← Python + FastAPI. Statement parsing, categorization, ML/LLM work.
├── infra/
│   └── docker-compose.yml ← postgres + all three services for local dev
├── legacy/                ← the 2021 codebase, frozen. Reference only. Never build it.
└── .github/workflows/     ← CI
```

**Rule:** `services/api` is the only thing that talks to PostgreSQL. `services/ai` is a stateless
worker that receives documents/transactions over HTTP and returns structured results. The web app
never calls the AI service directly.

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
| Categorization | `services/ai/src/finances_ai/categorize/` |

---

## First session checklist

When Allen starts a session on this repo, the useful opening moves are:

1. Read `docs/DECISIONS.md` and walk him through the open questions marked **OPEN**. Several are
   genuinely his call (build tool, ORM vs. SQL-first, Material vs. Tailwind, aggregator vendor).
2. Read `docs/DOMAIN.md` — the legacy budget model is more subtle than it looks, especially the
   credit-card handling. Confirm the parts he still wants before you migrate them.
3. Confirm milestone 1 scope in `docs/ROADMAP.md`, then build it.

Do not start writing feature code before steps 1–3.
