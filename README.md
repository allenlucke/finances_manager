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
| Web | Angular 22 | Standalone components, signals, Vitest |
| AI | Python 3.13 + FastAPI | Statement parsing, categorization; stateless |
| DB | PostgreSQL 18 | Flyway migrations, forward-only |

Several choices are still open for discussion — see [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Quick start

```bash
cp .env.example .env
make up          # postgres + api + web + ai via Docker Compose
open http://localhost:4200
```

Or run pieces natively:

```bash
make db          # postgres only
make api         # Spring Boot on :8080
make web         # Angular dev server on :4200 (proxies /api to :8080)
make ai          # FastAPI on :8000
make test        # every suite
```

`make help` lists everything.

## Documentation

| Doc | What's in it |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Project brief and working agreements for Claude Code |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service boundaries and request flow |
| [docs/DOMAIN.md](docs/DOMAIN.md) | The budget model, including the credit-card subtlety |
| [docs/ROADMAP.md](docs/ROADMAP.md) | M0 → M6 milestones |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Settled choices, and the open ones worth arguing about |
| [docs/SECURITY.md](docs/SECURITY.md) | Handling credentials and real financial data |

## Status

**M0 — scaffold.** The three services start, find each other, and report health. No features yet.
M1 (rebuilding the budgeting core) is the next milestone.
