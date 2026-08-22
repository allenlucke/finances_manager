# Architecture

```
                ┌─────────────────┐
                │  Angular 22 SPA │   services/web  :4200
                └────────┬────────┘
                         │ REST + JWT
                ┌────────▼────────────────────┐
                │  Spring Boot 4.1 API        │   services/api  :8080
                │  · domain + business rules  │
                │  · owns the database        │
                │  · Flyway migrations        │
                └───┬──────────────────┬──────┘
                    │ JDBC             │ HTTP (internal only)
          ┌─────────▼──────┐   ┌───────▼──────────────────┐
          │ PostgreSQL 18  │   │ FastAPI AI service       │  services/ai  :8000
          └────────────────┘   │ · statement parsing      │
                               │ · categorization         │
                               │ · forecasting / insights │
                               └──────────────────────────┘
```

## Boundaries

**services/api owns the truth.** All persistence, all business rules, all authorization. If a
calculation determines a number a human will act on, it happens here.

**services/ai is stateless and advisory.** It takes a document or a batch of transactions and
returns structured suggestions with confidence scores. It has no database credentials, holds no
state between calls, and its output is never applied without passing back through the API's rules.
It is reachable only on the internal network — never exposed to the browser.

**services/web is a client.** No business logic beyond presentation. Every number it displays came
from the API.

## Why the split

Java carries the money math, transaction integrity, and the schema — it's good at that and the
existing domain knowledge is already written in it. Python carries parsing and inference, because
that's where those libraries live. The seam between them is a plain HTTP contract with typed
payloads, so either side can be rewritten without the other noticing.

## Request flow: importing a statement

1. Browser uploads a file to `POST /api/v1/imports` on the API.
2. API stores the raw file, creates an `import_batch`, and forwards to the AI service.
3. AI service parses it into normalized transaction candidates and returns them with per-row
   confidence.
4. API deduplicates against existing transactions, persists what's new, and asks the AI service to
   categorize the new rows.
5. Low-confidence rows are queued for human review in the UI.
6. Every human correction is written back as training signal.

Steps 3–5 are the whole product. The rest is CRUD.
