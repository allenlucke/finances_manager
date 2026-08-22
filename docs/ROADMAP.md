# Roadmap

Milestones, not dates. Each one ends with something Allen can actually use.

## M0 — Scaffold stands up *(this commit)*
`make up` brings postgres, api, web, and ai online. Health checks pass. CI is green. Nothing does
anything useful yet, and that's fine.

**Done when:** all four containers healthy, `/actuator/health` and `/health` return OK, the Angular
app loads and shows live status from both backends.

## M1 — Budgeting core, rebuilt
Port the legacy domain to the new stack: users, periods, accounts, categories, budgets, expense and
income items, account periods. Auth. The balance-sheet queries from `legacy/db/budgetBalanceSheet.sql`
and `acctBalScratch.sql` are the reference for the reporting endpoints.

**Done when:** Allen can do everything the 2021 app did, with tests, on the new stack.
**Discuss first:** D-11 (persistence) and D-12 (auth) in `docs/DECISIONS.md`.

## M2 — Statement ingestion
CSV first (the legacy Django `reader_chase.py` shows the shape), then OFX/QFX, then PDF. The
`transaction` table, the `import_batch` table, idempotent re-import, and a review UI for
unmatched rows. No AI yet — deterministic parsing only.

**Done when:** Allen drops a real Chase export and a real Fidelity export in and the transactions
land correctly, twice in a row, with no duplicates.

## M3 — Categorization
The three tiers from D-15: rules, then similarity against his own history, then LLM fallback.
Corrections captured and fed back. Accuracy measured against a held-out set of his own transactions,
not vibes.

**Done when:** a month of new transactions comes in and the majority are correctly categorized
without intervention, and the number is *measured*.

## M4 — Multi-entity + investments
Personal vs. Feeling Froggy LLC separation. Securities, holdings, cost basis, market value. Net
worth across everything. Business expense flagging with an eye toward tax time.

## M5 — Aggregator connection
Whichever vendor wins D-14, behind the `AccountConnector` port built in M2. Automatic sync,
credential handling per `docs/SECURITY.md`, reconciliation against imported statements.

## M6 — Insight layer
Forecasting, anomaly detection ("this bill is 40% higher than usual"), cashflow projection, and a
natural-language query surface over the ledger. This is the part that needed a decade of fintech
to become reasonable, and it's now the easy part — but only if M2 and M3 produced clean data.

---

**The order matters.** Every interesting thing in M6 depends on trustworthy categorized data from
M3, which depends on reliable ingestion in M2, which depends on a sound model from M1. Resist
building M6 early on synthetic data; it will teach you the wrong things.
