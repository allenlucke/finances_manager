# Domain model

The 2021 app got the shape of this right, and the shape is what survives. Read
`legacy/db/pgInit.sql` alongside this.

## The legacy model (what exists in `legacy/`)

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
so the same category can carry a different allocation each period. Keep this.

**`accountPeriod` beginning/ending balances.** Per-account, per-period balance snapshots. This is
what makes reconciliation and balance sheets possible, and it's exactly the table you want when real
bank data starts flowing in — the imported statement's closing balance validates against it.

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

**`incomeItem.amountExpected` vs `amountReceived`.** Income is forecast then reconciled. Keep it.

## What the new model needs to add

- **`institution` / `connection`** — a linked financial provider (a bank, Fidelity, a file-import
  source) with its own sync state and credential reference. Credentials never live in this table;
  see `docs/SECURITY.md`.
- **`transaction` as the ingestion primitive.** Raw imported rows land here — date, amount,
  description, merchant, external ID, source, raw payload — *before* they become an `expenseItem`
  or `incomeItem`. Import must be idempotent: re-importing an overlapping statement cannot create
  duplicates. Dedupe on (account, date, amount, normalized description) plus provider ID when there
  is one.
- **`categorization`** — the suggested category, the confidence, the method (rule / similarity /
  model), and whether a human confirmed or corrected it. Corrections are the training signal; they
  must be stored, not just applied.
- **`holding` / `position` / `security`** — Fidelity and any brokerage need share quantity, cost
  basis, and market value. Fundamentally different from a cash transaction; don't force it into
  `expenseItem`.
- **`entity` or `ledger` scoping** — personal vs. Feeling Froggy LLC. Business and personal finances
  must be separable for tax purposes and joinable for net-worth purposes. Decide early whether this
  is a column on account or a full separate ledger dimension; retrofitting it is expensive.

## Naming

The legacy schema used quoted camelCase identifiers (`"expenseCategory"`, `"users_id"`), which means
every query needs double quotes forever. The new schema uses unquoted `snake_case`. Java/TypeScript
keep camelCase; mapping happens at the persistence boundary.
