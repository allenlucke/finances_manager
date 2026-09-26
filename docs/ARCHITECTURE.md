# Architecture

```
                ┌─────────────────┐            ┌──────────────────────────┐
                │  Angular 22 SPA │  :4200     │  Claude Code + MCP       │  services/mcp
                └────────┬────────┘            │  (D-17, runs natively)   │
                         │ REST, session cookie └────────────┬─────────────┘
                         │ + CSRF token                      │ REST, bearer token
                ┌────────▼───────────────────────────────────▼──┐
                │  Spring Boot 4.1 API                          │   services/api  :8080
                │  · domain + business rules                    │
                │  · owns the database                          │
                │  · Flyway migrations, reporting views         │
                └───┬──────────────────────────────┬────────────┘
                    │ JDBC                         │ HTTP/1.1 (compose network only)
          ┌─────────▼──────┐              ┌────────▼─────────────────┐
          │ PostgreSQL 18  │              │ FastAPI AI service       │  services/ai  :8000
          └────────────────┘              │ · statement parsing      │
                                          │ · categorization (M3)    │
                                          └──────────────────────────┘
```

## Boundaries

**services/api owns the truth.** All persistence, all business rules, all authorization. If a
calculation determines a number a human will act on, it happens here. Reporting SQL lives in
Flyway-created views (D-11), read through `JdbcTemplate`.

**services/ai is stateless and advisory.** It takes a document and returns normalized rows with
flags (`is_probable_transfer`, `is_probable_refund`) and per-row warnings; later, batches of rows
and returns category suggestions with confidence. It has no database credentials, holds no state
between calls, and its output is never applied without passing back through the API's rules. It is
reachable only on the compose network — never exposed to the browser. One configured secret,
`ACCOUNT_KEY_SECRET`, keys the one-way account ids it derives (docs/SECURITY.md).

**services/web is a client.** No business logic beyond presentation. Every number it displays came
from the API; nothing in the browser does arithmetic on money.

**services/mcp is a second client** (D-17): an MCP server over the same REST API, launched by Claude
Code, authenticating with a loopback-only bearer token. It adds no rules of its own and is not part
of the compose stack.

## Authentication

Server-side sessions in Postgres (Spring Session JDBC) carried by an `HttpOnly`, `SameSite=Lax`
cookie, with CSRF protection via the `XSRF-TOKEN` cookie the SPA echoes as a header. Passkeys are a
self-enforcing second factor: registering one makes it required. No JWTs anywhere — D-12 explains
why. The local automation token is the one deliberate bypass, and SECURITY.md describes its
boundaries.

## Why the split

Java carries the money math, transaction integrity, and the schema — it's good at that and the
existing domain knowledge is already written in it. Python carries parsing and inference, because
that's where those libraries live. The seam between them is a plain HTTP contract with typed
payloads — hand-mirrored records on the Java side, pinned by a captured fixture — so either side can
be rewritten without the other noticing.

## Request flow: importing a statement

1. Browser uploads a file to `POST /api/v1/imports` on the API, naming an account or leaving the
   file to name its own.
2. API opens an `import_batch` in its own transaction — so the record survives whatever happens
   next — and forwards the bytes to the AI service. **The file itself is never stored**
   (docs/SECURITY.md).
3. AI service parses it into normalized transaction candidates, each with its direction, the
   institution's own id when there is one, the account it belongs to when the file says, and the
   file's own row-type hints; plus per-row warnings for anything it could not read.
4. API resolves each row's account (creating nothing — unknown accounts are reported back for the
   person to create), computes the one dedupe identity, persists what is new, marks the file's
   payments as transfers, records the statement's closing balance as a reconciliation checkpoint,
   and keeps the parser's warnings on the batch.
5. Rows without a category wait in the review queue. Automatic categorization is M3: the API does
   not yet call `/categorize`.
6. Every human correction will be written back as training signal (the `categorization` table
   exists for it; M3 fills it).

Steps 3–5 are the whole product. The rest is CRUD.

## Request flow: an order (M7b, D-18)

Order execution is the one path in this system where a wrong request loses money irreversibly, so
it has its own record and its own gates rather than the ledger's trust model.

1. **Propose.** A person on the Markets screen, or the assistant through the MCP `propose_order`
   tool, drafts an order: symbol, side, quantity, market or limit, paper or manual, and a reason.
   The API sizes it against the latest stored quote (or the limit price) and records a `trade_order`
   in `draft` with a `trade_order_event` naming who proposed it. Nothing is sent.
2. **Confirm by restating.** `POST /api/v1/orders/{id}/confirm` carries an echo — symbol, side,
   quantity, limit — that must match the draft exactly; the browser has the person type the symbol
   back, the MCP tool is marked destructive so the client asks first. A mismatch is a 422 that
   quotes the draft. A manual order stops here, `confirmed`, waiting to be carried to Fidelity.
3. **The switch, then the cap.** `TRADING_ENABLED=false` (the default) keeps the confirmation and
   refuses to send, naming the variable. `TRADING_DAILY_CAP` is checked against what reached the
   broker today; over it, the order is cancelled with the figures on record. Both refusals commit —
   the state after a refusal is the evidence of it.
4. **The wire.** `BrokerClient` posts to the Python service's `/broker/orders` with our own
   `client_order_id`, so a retry after a timeout cannot place the order twice. The Python side holds
   the Alpaca keys and knows only the paper host. The broker's answer is folded into the state
   machine and recorded as a `broker` event.
5. **Sync.** Every market-refresh tick, and on demand, open orders are looked up and their fills,
   cancellations and rejections recorded. A fill changes nothing in the ledger: what is owned is
   learned from the next positions import, as it always was.

## Strategies and backtests (M7c, D-19)

The Python service owns the arithmetic; the API owns the record and the only side effect.

- **Bars** come from the market-data provider seam (`services/ai/.../market/provider.py`):
  Alpaca's paged bars endpoint on the IEX feed, or the fake provider's deterministic random walk.
  Nothing is stored; a backtest fetches what it needs.
- **Strategies** (`market/strategies.py`) are pure: `prepare(bars)` computes indicators once,
  `on_bar(index, position)` returns buy, sell or nothing having seen bars up to `index`. The
  catalog (`GET /strategies`) describes each one's parameters with defaults and bounds, and the
  web form and the MCP tools are built from it.
- **The backtester** (`market/backtest.py`, `POST /backtests`) simulates with next-bar fills,
  costs, whole shares, a buy-and-hold benchmark, an out-of-sample tail, and returns metrics, the
  equity curve, every trade with its reasons, and the warnings. The API stores the result whole
  in `backtest_run` with a summary beside it for lists, and never recomputes it.
- **Live** (`POST /strategies/evaluate`, `StrategyService.evaluateAll`, `StrategyScheduler`):
  the API asks each active strategy for its opinion on the newest bars, passing whether it is
  long (learned from its own filled orders). A new signal becomes a draft through
  `OrderService.propose` — proposed by the assistant, sized by the signal's bar, rationale from
  the strategy — and an ntfy note goes out. From there it is an M7b order: echo, switch, cap.

## The app speaks up (M8, D-20)

`DigestService.compose(userId)` reads the views and tables the system already has —
`v_statement_reconciliation`, `v_spend_vs_target`, `trade_order`, `reminder`, `statement` and
`holding` dates, `alert_event`, `strategy` — and returns one list of items: kind, severity, a
sentence with the number in it, and the screen to go to. Three callers, no other producer:

- `GET /api/v1/digest/preview`, behind the dashboard's Needs-a-look card and the MCP `needs_a_look` tool.
- `DigestService.send`, which pushes the list through the `Notifier` (ntfy) and records a
  `digest_run` with the body and the delivery outcome, whether or not anything went out.
- `DigestScheduler`, every ten minutes behind `finances.digest.scheduled`, which sends once per
  person per day after their chosen time when the digest is enabled for them.

Reminders (`/api/v1/reminders`) are the person's own dated items and feed the same list once they
are within their lead time.

## The ledger's rhythm (M9, D-21)

`RecurringService.report(userId, days)` reads `v_transaction_resolved` for the last four hundred
days (transfers and pending rows excluded), groups by account, direction and a normalized
merchant key, and turns steady groups into series with their evidence and status. It projects
them over the window into expected charges with totals, and lists anomalies. `GET /api/v1/cashflow`
serves the Cash flow screen and the MCP `recurring_charges` tool; `DigestService` reads the same
report for the needs-a-look list. `SnapshotService` writes `net_worth_snapshot` once a day from
the `DigestScheduler` tick (and on request), and `GET /api/v1/reports/net-worth/history` feeds the
dashboard's trend.

## Categorization (M3a, D-22)

`CategorizationService.suggestFor` runs after every import's rows are saved and on demand for the
review queue. Tier one is the person's own history: categorized rows from the last two years,
grouped by the recurring detector's merchant key; two agreeing rows are the minimum, and the
confidence rises with the count and falls with disagreement. Tier two is the rules table in the
Python service, asked over `POST /categorize` by `CategorizerClient` and answering by category
*name*, which the API maps to the person's categories. The higher confidence wins. At 0.90 with a
category the person has, the row is categorized and the `categorization` row is `applied`; below
that, or naming a category they lack, the suggestion waits on the review row with its reason.
`TransactionController.categorize` resolves the suggestion with the person's decision, and
`categorization-stats` reports precision from those decisions. The categorizer being down is a
note on the import, never a failed import.

## The system's account of itself

`GET /api/v1/system/status` gathers what `make doctor` prints from the outside — the AI service's
health, the market-data provider and the last refresh, the broker and the trading switch, the
digest's schedule and last run, the last net-worth snapshot, the live strategies, the notification
channel — for the System card on Sign-in security and the MCP `system_status` tool.
