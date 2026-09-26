# finances_manager

Personal and small-business finance platform — budgeting, statement ingestion, automatic
categorization, and (eventually) an AI layer over the whole financial picture.

Originally built 2020–2021 on Angular 12 / Spring Boot 2.4 / PostgreSQL. That code is preserved
under [`legacy/`](legacy/) and is reference material only. This branch is a ground-up rebuild that
keeps the domain model and replaces the implementation.

## Stack

| Piece | Choice | Notes |
|---|---|---|
| API | Java 25 (LTS) + Spring Boot 4.1 | System of record; owns the database |
| Web | Angular 22 + Angular Material | Standalone components, signals, Vitest, Playwright |
| AI | Python 3.13 + FastAPI | Statement parsing, categorization; stateless |
| MCP | Python | Lets Claude Code drive the API (D-17) |
| DB | PostgreSQL 18 | Flyway migrations, forward-only; reporting in SQL views |

Every choice is settled and recorded with its reasoning in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Quick start

```bash
make up          # postgres + api + web + ai via Docker Compose; creates .env and its secrets
open http://localhost:4200
```

Or run pieces natively:

```bash
make db          # postgres only
make api         # Spring Boot on :8080
make web         # Angular dev server on :4200 (proxies /api to :8080)
make ai          # FastAPI on :8000
make test        # every unit suite
make e2e         # browser tests on a throwaway stack (4201/8081)
```

`make help` lists everything.

## Documentation

| Doc | What's in it |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Project brief and working agreements, each one earned by a bug |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service boundaries, authentication, and the import flow |
| [docs/DOMAIN.md](docs/DOMAIN.md) | The money model, including the credit-card subtlety |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones, with what each one found when it met real data |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Settled choices and why they won |
| [docs/SECURITY.md](docs/SECURITY.md) | Credentials, real financial data, the automation token, backups |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | Deploying on the homelab, backups and restores, rotation, and what to do when something is lost |
| [docs/REVIEW-2026-09-11.md](docs/REVIEW-2026-09-11.md) | The current review ledger: findings, and which batch closed each |
| [docs/COWORK-QA-BRIEF.md](docs/COWORK-QA-BRIEF.md) | The brief for an outside QA pass |

## Status

**M1 through M2.5 built; M4 partly; M7a and M7b built.** The budgeting core, statement import
(CSV, OFX/QFX) with idempotent re-import and reconciliation checkpoints, passkeys, holdings from a
positions export, conversational control through Claude Code, a watchlist with price alerts and
holdings valued at the latest quote, paper orders behind a restated confirmation, a kill switch
and a daily cap, strategies with an honest backtester that can propose drafts but never trade, and
a daily digest of what needs a look with reminders, recurring charges found in the ledger with a
cash-flow forecast, and net worth kept by day. Two review passes have been worked through; the
ledger above says what remains. M3 (automatic categorization) is next.
