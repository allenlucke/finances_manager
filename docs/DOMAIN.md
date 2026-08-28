# Domain model

The 2021 app got several *insights* right. Its **shape** does not survive — the workflow was
rebuilt from scratch on 2026-08-22 (Allen's call). Read the legacy section for context, then
**"The rebuilt model" below is what M1 actually builds.** Read `legacy/db/pgInit.sql` alongside this.

## The legacy model (what exists in `legacy/`) — historical, not the target

```
users
  └── period            (a named date range: "March 2021", startDate → endDate)
        └── budget      (one budget belongs to one period; can be closed)
              ├── budget_expenseCategory   (junction: budget × expenseCategory + amountBudgeted)
              │     └── expenseItem        (an actual spend)
              └── budget_incomeCategory    (junction: budget × incomeCategory + amountBudgeted)
                    └── incomeItem         (amountExpected vs. amountReceived)
  ├── account           (isCredit flag distinguishes credit cards from asset accounts)
  │     └── accountPeriod  (beginningBalance / endingBalance per account per period)
  ├── expenseCategory
  └── incomeCategory
```

### The parts that are cleverer than they look

**Period-scoped budgeting.** Budgets are anchored to explicit user-defined periods rather than
calendar months. Categories are global per user; the *budgeted amount* lives on the junction row,
so the same category can carry a different allocation each period.
> ~~Keep this.~~ **Removed.** Requiring the user to hand-create a period before budgeting in it was
> ceremony, not modelling. Replaced by effective-dated `target` rows plus ad-hoc date ranges.

**`accountPeriod` beginning/ending balances.** Per-account, per-period balance snapshots. This is
what makes reconciliation and balance sheets possible, and it's exactly the table you want when real
bank data starts flowing in — the imported statement's closing balance validates against it.
> **Kept, renamed `statement`, no longer user-defined** — the cycle arrives inside the import.

**Credit card handling on `expenseItem`.** Three booleans and a self-reference, and the comments in
the legacy schema explain the reasoning:
- `paidWithCredit` — the purchase was made on a card.
- `paymentToCreditAccount` — this row is a *payment* on a card balance, not a new expense. It
  deliberately has a **null** `budget_expenseCategory_id`, because the budget was already charged
  when the purchase happened. Categorizing it again would double-count the spend.
- `interestPaymentToCreditAccount` — interest, allocated to the period the card was paid in.
- `payToCreditAcctId` — which card the payment went to.

That double-counting insight is the single most important piece of business logic in the old app.
Any rewrite must preserve it, and any auto-categorizer must learn to recognize card payments and
transfers as **non-expenses**.
> **Kept and hardened** — now `is_transfer` + `transfer_account_id` with a CHECK constraint forcing
> `category_id` NULL, so the rule is enforced by the database rather than by convention.

**`incomeItem.amountExpected` vs `amountReceived`.** Income is forecast then reconciled.
> **Kept, generalized** — the forecast is a `target` on an income category; the actual is a
> `transaction`. Same mechanism as budgeted-vs-spent.

## The rebuilt model (what M1 actually builds)

Decided 2026-08-22. The legacy *shape* is not preserved — the legacy *insights* are. Allen's call:
the old workflow was a chore that bought nothing, so it goes.

### What was removed, and why

| Legacy | Rebuilt | Why |
|---|---|---|
| `period` (user-defined named date range) | **gone** | Planning windows are query parameters, not rows. Ask for August, get August. Creating "March 2021" by hand before you could budget in it was pure ceremony. |
| `budget`, `budget_expenseCategory`, `budget_incomeCategory` | `target` | An effective-dated amount per category. Set it once; it applies until changed. No per-period rows, no "closing" a budget. |
| `expenseCategory` + `incomeCategory` | one `category` with `kind` | Same table twice with a different word in the name. |
| `expenseItem` + `incomeItem` | one `transaction` with `direction` | Same shape with an opposite sign. Every legacy report had to union them. |
| `incomeItem.amountExpected` | `target` on an income category | Expected-vs-actual is the same idea as budgeted-vs-spent. One mechanism. |
| `accountPeriod` | `statement` | Keeps the reconciliation checkpoint, drops the manual period. Statement cycles arrive *inside* the imported statement — you never type one in. |
| `accountTracker` (abandoned; see below) | window function at read time | Never materialize a running balance. |

### The two concepts the legacy `period` conflated

This is why it needed to be user-defined, and why removing it is safe:

* **Planning windows** — "how am I doing this month." Arbitrary, ad-hoc, belong in a `WHERE` clause.
* **Statement cycles** — the closing balance an institution asserts on a real date. Not chosen by
  the user, often not a calendar month (a card may close on the 18th), and the only thing that can
  *prove* the ledger matches reality.

One table serving both forced manual entry. Split, planning becomes free and reconciliation gets
stronger.

### The `accountTracker` lesson

`legacy/db/postExpenseItem.sql` and `spScratch.sql` reference an `accountTracker` table — a
materialized running balance, one row per transaction. It is **not** in `pgInit.sql`, not in the
seed data, and not referenced by any application code. It was started and abandoned.

Abandoning it was correct, and the reason governs the rebuild. It derived each row's balance from
"the last row on or before this date," so **inserting a transaction out of order silently corrupts
every balance after it** — and importing March after April is exactly what M2 does, routinely.
(The abandoned procedure also hardcoded `account_id` to `1`.)

**Rule: running balances are computed with a window function at read time, never stored.**
Statement closing balances are the checkpoints that prove the arithmetic.

### What survives from 2021, unchanged in spirit

1. **The credit-card double-count rule.** A payment to a card is *not* an expense — the budget was
   charged when the purchase happened. In the new model this is `is_transfer = true` with
   `transfer_account_id` pointing at the card, and `category_id` forced NULL — **enforced by a CHECK
   constraint**, not by convention. The old `paidWithCredit` flag is gone because it is implicit in
   the account's type, and `interestPaymentToCreditAccount` is gone because interest is simply a
   real expense with a category; the special handling existed only to allocate it to a period.
2. **Forecast then reconcile.** Income is expected, then received. Now generalized to `target`.
3. **Reconciliation against a real closing balance.**

### Entity scoping — personal vs. Feeling Froggy LLC

Feeling Froggy **generates its own financials and sends them over** (MCP or API, decided at M5). So
FF is a *source*, not a second set of books modelled here: it is one more implementation behind the
`AccountConnector` port from D-14, and provenance separates most of it for free.

`ledger_entity` exists anyway, with a **nullable override on `transaction` defaulting from the
account**. The case that provenance cannot solve is a *personal* card carrying a *business*
expense, which is exactly the case that matters at tax time and can only be resolved per
transaction. One column now; a painful migration later.

### Ingestion primitives, built in M1 ahead of M2/M3

Per D-14, the schema absorbs aggregator semantics up front so M5 is an adapter, not a migration:

* **`transaction` is the single primitive.** Imported rows land here directly — there is no separate
  staging shape, because there is no longer an `expenseItem`/`incomeItem` to be promoted into.
* **Idempotent import** — `UNIQUE (account_id, dedupe_key)` makes re-importing an overlapping
  statement a no-op at the database level, not a matter of application discipline.
* **Pending-transaction linkage** — `pending` plus `pending_external_id`. Aggregators replace a
  pending row with a posted row having a *different* ID, date, and sometimes amount; the dedupe key
  alone would double-count that transition.
* **Provenance** — `source`, `connection_id`, `import_batch_id`.
* **`categorization`** — suggestion, confidence, method, and how a human resolved it. Corrections
  are the tier-2 training signal and the only way M3 can measure accuracy rather than guess.

### Sign convention

`amount` is always a positive magnitude; `direction` carries the sign. **Debit contributes
negative, credit positive — for every account type, credit cards included.** A card balance goes
negative as you spend and toward zero as you pay it down, so a negative balance means *owed*.

The payoff: **net worth is a plain `SUM` across all accounts**, with no per-type sign flipping and
no `CASE` on `account_type`. Liabilities reduce it because they are already negative.

### Transfers are single-sided

One row is one account's *view* of a money movement, because that is exactly what a statement
reports. Import both the checking and the card statement and you naturally get two rows — one per
account, each with its own `dedupe_key` — which net to zero across the pair. Net worth stays
correct with no double-entry bookkeeping.

`transfer_account_id` is therefore a **link** ("the other side is over there"), not a second ledger
leg. `transfer_group_id` ties a matched pair together once both sides are known.

### Tenant integrity

Every scoped table carries `user_id` **and** a composite FK `(child_id, user_id)` →
`(id, user_id)`, backed by a `UNIQUE (id, user_id)` on each parent. A plain single-column FK would
let a transaction owned by one user reference another user's account — the database could not tell,
and the bug would surface as one person seeing another's money.

This matters even at one user: it is the difference between "we never wrote that bug" and "that bug
is unrepresentable," and it is expensive to retrofit once data exists.

### Deliberate non-constraints

Two things a stricter schema would enforce, and why this one does not:

* **No `posted_date >= transaction_date` check.** Institutions do occasionally report a post date
  *before* the transaction date (pre-authorization reversals, corrected postings). A hard
  constraint would fail an entire real statement import over a data-quality quirk. Anomalies belong
  in the M2 review queue.
* **Deletion is soft (`deleted_at`), not hard.** The dedupe index deliberately spans deleted rows,
  so removing an imported transaction cannot be undone by re-importing the same statement. A hard
  `DELETE` would free the `dedupe_key` and silently resurrect the row on the next import.

### How the model held up against real files (2026-08-27)

Three real exports from two institutions — a credit-union checking history, a brokerage transaction
history, and a brokerage positions snapshot — went through the schema. What it got right:

* **One `transaction` with a direction** absorbed bank rows, card rows and brokerage activity
  without a special case. The collapse of `expenseItem`/`incomeItem` was correct.
* **Single-sided transfers with `transfer_group_id`** were vindicated by evidence rather than
  argument: 8 outflows in the checking file matched 8 deposits in the brokerage file, on amount and
  date, 8 for 8. The same money from both sides, which is exactly what the design assumed.
* **`ledger_entity`** separated personal from Feeling Froggy without retrofitting, which was the
  reason for putting it in early.
* **`statement` as a checkpoint** reconciled an imported OFX to zero against the institution's own
  closing balance.

What it got wrong, found by using it:

* **The import link was not unique** (fixed in V4). `external_id` is now also the link between an
  imported row and an account, but the V2 index scoped it to `(connection_id, external_id)` — and an
  imported account has no connection. A unique index containing a NULL constrains nothing in
  PostgreSQL, so duplicates were silently permitted; creating the same account twice then broke
  every later import of that file. Now unique per user.
* **`transaction.external_id` carried the same flaw** and is now unique per account.

Still absent, and deliberately so: **`security` / `holding`**. A brokerage buy currently lands as an
ordinary transaction with its symbol, quantity and price surviving only inside `raw`. That is
acceptable while positions are not persisted, and is precisely the gap M4 exists to close — the
positions parser already produces exactly the fields those tables need.

### Deferred

`security` / `holding` / `position` are **M4**, not M1. Share quantity, cost basis, and market value
are genuinely different from a cash transaction and must not be forced into `transaction` — but
building them speculatively before there is brokerage data would be guesswork.

## Naming

The legacy schema used quoted camelCase identifiers (`"expenseCategory"`, `"users_id"`), which means
every query needs double quotes forever. The new schema uses unquoted `snake_case`. Java/TypeScript
keep camelCase; mapping happens at the persistence boundary.
