-- =================================================================================================
-- M4: securities and holdings.
--
-- A positions export is a SNAPSHOT, not a history: quantity, price, market value and cost basis at
-- one moment. It records no money movement, so none of it belongs in `transaction` and the
-- debit/credit sign convention does not apply to it. Market value is a magnitude, not a direction.
--
-- THE CONSEQUENCE THAT MATTERS, and the reason this migration also rewrites the balance view:
--
--   A brokerage account's value is what its holdings are worth. It is NOT the sum of the cash that
--   moved into it. Deposit $10,000 over three years, have it grow to $14,000, and the transaction
--   sum still says $10,000 — understating net worth by every dollar of gain, silently, with a
--   figure that looks entirely reasonable.
--
-- So for an account with a holdings snapshot, the balance comes from the snapshot. Transactions on
-- that account are still recorded and still matter (they are how transfers in and out get matched),
-- but they no longer decide what it is worth. Fidelity's export includes money-market and cash rows
-- (SPAXX, USD) as holdings, so the snapshot covers uninvested cash too and nothing is missed.
--
-- The honest cost: a snapshot has a date, and between snapshots the figure is stale. That is
-- inherent to snapshots, so `v_account_balance` exposes `balance_as_of` and `balance_source` rather
-- than hiding it. A stale number you can see the date of is worth far more than a fresh-looking
-- number that is wrong.
-- =================================================================================================


-- An instrument. Shared across accounts: the same fund held in three accounts is one security.
CREATE TABLE security (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Ticker with footnote markers already stripped by the parser: Fidelity writes SPAXX** and
    -- USD***, and the asterisks are not part of the symbol.
    symbol        VARCHAR(32)  NOT NULL,
    name          VARCHAR(255),
    security_type VARCHAR(20)  NOT NULL DEFAULT 'unknown',
    -- Decided by symbol, never by the export's `Type` column. That column is the account's
    -- registration (Cash or Margin), and reading it as "is this cash" classified AAPL as cash.
    is_cash       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_security_type CHECK (security_type IN
        ('equity', 'etf', 'mutual_fund', 'money_market', 'bond', 'crypto', 'cash', 'unknown')),
    CONSTRAINT ux_security_symbol UNIQUE (user_id, symbol),
    CONSTRAINT ux_security_tenant UNIQUE (id, user_id)
);


-- One instrument's position in one account, as of one date.
CREATE TABLE holding (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    account_id        BIGINT      NOT NULL,
    security_id       BIGINT      NOT NULL,
    -- The snapshot date, from the export's own "Date downloaded" line. Part of the key: keeping
    -- successive snapshots is what makes a position's history visible at all, since a positions
    -- file contains no history of its own.
    as_of             DATE        NOT NULL,

    -- NOT money, so not NUMERIC(19,4). Mutual funds settle to three decimals, brokerages report
    -- more, and crypto goes to eight. Rounding a share count to four decimals would quietly change
    -- what someone owns.
    quantity          NUMERIC(28,8),
    -- A price is money but needs finer resolution than a ledger amount: sub-cent quotes are normal.
    last_price        NUMERIC(19,6),

    -- Money. NUMERIC(19,4) like everything else. This one is NOT NULL because it is the whole
    -- point of the row — a holding whose value is unknown cannot contribute to a balance, and a
    -- silent zero would understate net worth.
    market_value      NUMERIC(19,4) NOT NULL,

    -- Nullable on purpose. Cash and money-market rows have no cost basis, and the export writes
    -- `--` for not-applicable, which is not the same as zero.
    cost_basis        NUMERIC(19,4),
    average_cost      NUMERIC(19,6),
    total_gain_loss   NUMERIC(19,4),

    import_batch_id   BIGINT,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_holding_account FOREIGN KEY (account_id, user_id)
        REFERENCES account (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_holding_security FOREIGN KEY (security_id, user_id)
        REFERENCES security (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_holding_batch FOREIGN KEY (import_batch_id, user_id)
        REFERENCES import_batch (id, user_id),
    -- Re-importing the same file updates the row rather than adding a second copy of the position.
    CONSTRAINT ux_holding_snapshot UNIQUE (account_id, security_id, as_of),
    CONSTRAINT ck_holding_quantity CHECK (quantity IS NULL OR quantity >= 0)
);

CREATE INDEX ix_holding_account_as_of ON holding (account_id, as_of DESC);
CREATE INDEX ix_holding_security ON holding (security_id);
CREATE INDEX ix_holding_user_as_of ON holding (user_id, as_of DESC);


-- The most recent snapshot per account, and what it was worth.
--
-- DISTINCT ON rather than a window function or a correlated subquery: it is the cheapest way to
-- say "the newest as_of per account" in Postgres, and it reads as what it means.
CREATE OR REPLACE VIEW v_latest_holding_snapshot AS
SELECT DISTINCT ON (account_id)
       user_id,
       account_id,
       as_of
FROM holding
ORDER BY account_id, as_of DESC;


CREATE OR REPLACE VIEW v_account_market_value AS
SELECT h.user_id,
       h.account_id,
       s.as_of                                  AS as_of,
       SUM(h.market_value)::NUMERIC(19,4)       AS market_value,
       SUM(h.cost_basis)::NUMERIC(19,4)         AS cost_basis,
       COUNT(*)                                 AS holding_count
FROM holding h
JOIN v_latest_holding_snapshot s
  ON s.account_id = h.account_id AND s.as_of = h.as_of
GROUP BY h.user_id, h.account_id, s.as_of;
