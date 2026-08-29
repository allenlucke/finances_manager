-- Reporting views (D-11).
--
-- A REPEATABLE migration (R__), not a versioned one: Flyway re-applies it whenever its checksum
-- changes and always after the versioned migrations. Views get edited far more often than tables,
-- and a new V-number per tweak would bury the schema history in noise. Every statement is
-- CREATE OR REPLACE so re-running is a no-op when nothing changed.
--
-- WHY VIEWS AT ALL: D-11 chose JPA, and Hibernate does not generate this SQL. Rather than scatter
-- native @Query strings through repository interfaces, the reporting SQL lives here — versioned,
-- reviewable, and next to the schema it depends on. Spring Data projections read these.
--
-- EVERY view below obeys two invariants. Getting either wrong produces a plausible wrong number:
--   1. deleted_at IS NULL          — deletion is soft; unfiltered queries count deleted money.
--   2. debit negative, credit positive, for all account types (see V2's SIGN CONVENTION).
--      Balances are therefore summed, never CASEd on account_type; a liability is simply negative.

-- ---------------------------------------------------------------------------------------------
-- Effective entity: a transaction may override its account's entity.
-- ---------------------------------------------------------------------------------------------

-- The one place the COALESCE lives. Everything downstream reads it, so "which entity does this
-- transaction belong to" has exactly one answer — the personal-card-business-expense case from
-- docs/DOMAIN.md is resolved once rather than in each report.
CREATE OR REPLACE VIEW v_transaction_resolved AS
SELECT t.id,
       t.user_id,
       t.account_id,
       a.account_type,
       COALESCE(t.ledger_entity_id, a.ledger_entity_id) AS ledger_entity_id,
       t.category_id,
       t.transaction_date,
       t.posted_date,
       t.amount,
       t.direction,
       t.currency,
       t.description,
       t.merchant,
       t.is_transfer,
       t.transfer_account_id,
       t.pending,
       t.source,
       -- The signed contribution. Computed once here so no downstream view re-derives the sign.
       CASE WHEN t.direction = 'debit' THEN -t.amount ELSE t.amount END AS signed_amount
FROM transaction t
JOIN account a ON a.id = t.account_id
WHERE t.deleted_at IS NULL;


-- ---------------------------------------------------------------------------------------------
-- Balances
-- ---------------------------------------------------------------------------------------------

-- Current balance per account. LEFT JOIN so an account with no transactions reports 0 rather than
-- vanishing from the list.
--
-- TWO SOURCES, and which one wins matters (M4, V5). For most accounts the balance is the sum of
-- its transactions. For an account with a holdings snapshot — a brokerage — it is the market value
-- of those holdings instead, because the transaction sum is the cash that went *in*, not what it
-- grew to. Summing deposits would understate a brokerage by every dollar of gain, and would do it
-- with a figure that looks perfectly reasonable.
--
-- Fidelity's positions export lists money-market and cash rows (SPAXX, USD) as holdings, so the
-- snapshot covers uninvested cash too — switching sources drops nothing.
--
-- `balance_source` and `balance_as_of` are exposed rather than hidden. A market value is only as
-- current as its last snapshot, and a stale figure whose date you can see beats a fresh-looking one
-- that is wrong.
CREATE OR REPLACE VIEW v_account_balance AS
SELECT a.user_id,
       a.id                AS account_id,
       a.name              AS account_name,
       a.account_type,
       a.ledger_entity_id,
       a.currency,
       a.is_active,
       COALESCE(mv.market_value, t.transaction_balance, 0)::NUMERIC(19,4) AS balance,
       COALESCE(t.transaction_count, 0)                                   AS transaction_count,
       t.last_activity,
       CASE WHEN mv.market_value IS NOT NULL THEN 'holdings' ELSE 'transactions' END AS balance_source,
       mv.as_of                                                           AS balance_as_of,
       mv.cost_basis
FROM account a
LEFT JOIN (
    SELECT r.account_id,
           SUM(r.signed_amount)   AS transaction_balance,
           COUNT(r.id)            AS transaction_count,
           MAX(r.transaction_date) AS last_activity
    FROM v_transaction_resolved r
    GROUP BY r.account_id
) t ON t.account_id = a.id
LEFT JOIN v_account_market_value mv ON mv.account_id = a.id;


-- Net worth, per entity and overall. Because liabilities are already negative this is a plain SUM:
-- no CASE on account_type, which is the payoff of the sign convention.
CREATE OR REPLACE VIEW v_net_worth AS
SELECT user_id,
       ledger_entity_id,
       SUM(balance)::NUMERIC(19,4) AS net_worth
FROM v_account_balance
GROUP BY GROUPING SETS ((user_id, ledger_entity_id), (user_id));


-- Running balance per account, computed at read time and never stored.
--
-- The legacy app tried to materialize this in an `accountTracker` table and abandoned it. That
-- approach cannot survive statement import: it derived each row's balance from "the last row on or
-- before this date", so inserting March after April silently corrupted everything after it. A
-- window function has no such failure mode. See docs/DOMAIN.md → The accountTracker lesson.
CREATE OR REPLACE VIEW v_running_balance AS
SELECT r.id AS transaction_id,
       r.user_id,
       r.account_id,
       r.transaction_date,
       r.description,
       r.signed_amount,
       SUM(r.signed_amount) OVER (
           PARTITION BY r.account_id
           ORDER BY r.transaction_date, r.id
           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       )::NUMERIC(19,4) AS running_balance
FROM v_transaction_resolved r;


-- ---------------------------------------------------------------------------------------------
-- Spending
-- ---------------------------------------------------------------------------------------------

-- Monthly net spend per category and entity.
--
-- Transfers are excluded, and that exclusion IS the credit-card double-count rule: a payment to a
-- card is not spending, because the budget was charged when the purchase happened. Including
-- transfers here would double-count every card payment — the exact bug the 2021 schema's comments
-- warned about.
--
-- Sign is flipped back to "positive means spent" because that is how a spend report reads; refunds
-- arrive as credits and correctly reduce the total.
CREATE OR REPLACE VIEW v_monthly_category_spend AS
SELECT r.user_id,
       r.ledger_entity_id,
       r.category_id,
       c.name  AS category_name,
       c.kind  AS category_kind,
       DATE_TRUNC('month', r.transaction_date)::DATE AS month,
       SUM(-r.signed_amount)::NUMERIC(19,4)          AS net_amount,
       COUNT(*)                                      AS transaction_count
FROM v_transaction_resolved r
JOIN category c ON c.id = r.category_id
WHERE r.is_transfer = FALSE
  AND r.category_id IS NOT NULL
GROUP BY r.user_id, r.ledger_entity_id, r.category_id, c.name, c.kind,
         DATE_TRUNC('month', r.transaction_date);


-- Spend against the target in force for that month.
--
-- The join is on the target's effective range rather than on a month column, because a target is
-- an open-ended interval rather than a per-month row — that is what removing user-defined periods
-- bought. At most one target can match: the ex_target_no_overlap exclusion constraint makes
-- overlapping ranges unstorable, so this cannot silently pick between two contradictory numbers.
--
-- target_amount is NULL where no target exists, which is the normal case: targets are optional and
-- most categories are better served by a baseline derived from history (M3/M6).
CREATE OR REPLACE VIEW v_spend_vs_target AS
SELECT s.user_id,
       s.ledger_entity_id,
       s.category_id,
       s.category_name,
       s.category_kind,
       s.month,
       s.net_amount,
       s.transaction_count,
       tg.amount   AS target_amount,
       tg.cadence  AS target_cadence,
       CASE WHEN tg.amount IS NULL THEN NULL
            ELSE (tg.amount - s.net_amount)::NUMERIC(19,4)
       END AS remaining
FROM v_monthly_category_spend s
LEFT JOIN target tg
       ON tg.category_id = s.category_id
      AND tg.ledger_entity_id = s.ledger_entity_id
      AND tg.effective_from <= s.month
      AND (tg.effective_to IS NULL OR tg.effective_to > s.month);


-- ---------------------------------------------------------------------------------------------
-- Reconciliation
-- ---------------------------------------------------------------------------------------------

-- Does the ledger agree with what the institution said?
--
-- This is the point of keeping statement checkpoints at all, and the M1a "done when" test. A
-- non-zero difference means the ledger is missing transactions, has duplicates, or has a wrong
-- amount — and it says so in dollars rather than requiring a manual tally.
CREATE OR REPLACE VIEW v_statement_reconciliation AS
SELECT st.id AS statement_id,
       st.user_id,
       st.account_id,
       st.period_start,
       st.period_end,
       st.opening_balance,
       st.closing_balance,
       COALESCE((
           SELECT SUM(r.signed_amount)
           FROM v_transaction_resolved r
           WHERE r.account_id = st.account_id
             AND r.transaction_date <= st.period_end
       ), 0)::NUMERIC(19,4) AS computed_balance,
       (st.closing_balance - COALESCE((
           SELECT SUM(r.signed_amount)
           FROM v_transaction_resolved r
           WHERE r.account_id = st.account_id
             AND r.transaction_date <= st.period_end
       ), 0))::NUMERIC(19,4) AS difference,
       st.reconciled_at
FROM statement st;
