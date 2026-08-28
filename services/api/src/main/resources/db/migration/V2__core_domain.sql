-- V2 — the rebuilt core domain.
--
-- This is NOT a port of legacy/db/pgInit.sql. The 2021 workflow (user-defined periods, per-period
-- budget rows, separate expense/income tables) was removed deliberately on 2026-08-22.
-- Read docs/DOMAIN.md → "The rebuilt model" for what changed and why before altering anything here.
--
-- Conventions, per CLAUDE.md:
--   * snake_case, unquoted identifiers
--   * money is NUMERIC(19,4), never float, at any layer
--   * instants are TIMESTAMPTZ stored UTC
--   * calendar dates that an institution reports (transaction/posted/statement dates) are DATE.
--     They are not instants: a purchase "on the 14th" is the 14th in the bank's reporting, and
--     forcing it through a timezone would move it.
--   * enumerations are VARCHAR + CHECK rather than PostgreSQL ENUM types — adding a value stays a
--     one-line migration and maps cleanly to Java without a custom Hibernate type.
--
-- ============================================================================================
-- SIGN CONVENTION — read before writing any balance or reporting query.
-- ============================================================================================
-- `amount` is ALWAYS a positive magnitude. `direction` carries the sign:
--     debit  → money leaving the account  → contributes NEGATIVE
--     credit → money entering the account → contributes POSITIVE
--
-- This holds for EVERY account type, credit cards included. A card purchase is a debit, so a
-- card's balance goes negative as you spend and moves toward zero as you pay it down. A negative
-- balance means "owed".
--
-- The payoff: net worth is a plain SUM across all accounts — no per-type sign flipping, no CASE on
-- account_type. Liabilities reduce it because they are already negative. Transfers are
-- single-sided (see `transaction`) and net to zero across the pair, so they neither inflate nor
-- deflate net worth.
--
-- ============================================================================================
-- TENANT INTEGRITY
-- ============================================================================================
-- Every scoped table carries user_id AND a composite FK (child_id, user_id) → (id, user_id) on its
-- parent, backed by a UNIQUE (id, user_id) on each parent. A plain single-column FK would let a
-- transaction owned by user A reference an account owned by user B — the database could not tell,
-- and the bug surfaces as one person seeing another's money.
--
-- This matters even at one user: it is the difference between "we never wrote that bug" and "that
-- bug is unrepresentable", and it is expensive to retrofit once data exists. Composite FKs use the
-- default MATCH SIMPLE, so a NULL child_id satisfies the constraint — exactly right for the
-- optional references (category, transfer account, entity).
--
-- Migrations are forward-only. Never edit this file once applied — add a new one.

-- Needed for the no-overlapping-targets exclusion constraint below (equality on scalar columns
-- combined with range overlap in a single GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- Sets updated_at on UPDATE. A trigger rather than JPA @UpdateTimestamp so the guarantee also
-- holds for migrations, imports, and any direct SQL — this is a system of record.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------------------------

-- "user" is reserved in SQL; app_user avoids quoting it forever.
-- Credentials for passkeys/sessions arrive in V3 with the auth work (D-12).
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    -- First factor. Argon2id/bcrypt via Spring Security defaults — never a plaintext or reversible
    -- value. Nullable so a future passkey-only account is possible without a migration.
    password_hash VARCHAR(255),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Email comparison is case-insensitive; store as entered, match lowercased.
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));
CREATE TRIGGER trg_app_user_updated BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Personal vs. Feeling Froggy LLC. A table rather than a boolean because an entity has a name and
-- will later carry tax attributes, and because a third entity should not require a migration.
CREATE TABLE ledger_entity (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_entity_kind CHECK (kind IN ('personal', 'business')),
    CONSTRAINT ux_ledger_entity_name UNIQUE (user_id, name),
    -- Composite-FK target; see TENANT INTEGRITY at the top.
    CONSTRAINT ux_ledger_entity_tenant UNIQUE (id, user_id)
);
CREATE TRIGGER trg_ledger_entity_updated BEFORE UPDATE ON ledger_entity
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Sources: institution → connection → account
-- ---------------------------------------------------------------------------------------------

CREATE TABLE institution (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name       VARCHAR(160) NOT NULL,
    -- business_system is Feeling Froggy: it generates its own financials and pushes them over
    -- (MCP or API, decided at M5). It is a source like any bank, not a second set of books.
    kind       VARCHAR(30)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_institution_kind
        CHECK (kind IN ('bank', 'credit_card_issuer', 'brokerage', 'business_system', 'manual')),
    CONSTRAINT ux_institution_name UNIQUE (user_id, name),
    CONSTRAINT ux_institution_tenant UNIQUE (id, user_id)
);
CREATE TRIGGER trg_institution_updated BEFORE UPDATE ON institution
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- One linked source with its own sync state. The D-14 AccountConnector port has one row per
-- configured connector; file import is simply the first implementation.
--
-- SECURITY.md: credentials NEVER live here. credential_ref is an opaque pointer into whatever
-- secret store is chosen before the first real token is stored — not the token, not a key.
CREATE TABLE connection (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    institution_id  BIGINT       NOT NULL,
    name            VARCHAR(160) NOT NULL,
    connector_type  VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    credential_ref  VARCHAR(255),
    last_synced_at  TIMESTAMPTZ,
    last_error      TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_connection_institution FOREIGN KEY (institution_id, user_id)
        REFERENCES institution (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_connection_type
        CHECK (connector_type IN ('file_import', 'plaid', 'snaptrade', 'mcp', 'api')),
    CONSTRAINT ck_connection_status
        CHECK (status IN ('active', 'needs_reauth', 'error', 'disabled')),
    CONSTRAINT ux_connection_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_connection_user ON connection (user_id);
CREATE TRIGGER trg_connection_updated BEFORE UPDATE ON connection
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE account (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    ledger_entity_id BIGINT       NOT NULL,
    institution_id   BIGINT,
    connection_id    BIGINT,
    name             VARCHAR(160) NOT NULL,
    -- Replaces the legacy is_credit boolean. Whether a balance is a liability is derivable from
    -- the type, so there is no second flag to keep in sync.
    account_type     VARCHAR(30)  NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'USD',
    -- SECURITY.md: last four only, unless there is a concrete reason for the full value.
    mask             VARCHAR(4),
    external_id      VARCHAR(255),
    opened_on        DATE,
    closed_on        DATE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_account_entity FOREIGN KEY (ledger_entity_id, user_id)
        REFERENCES ledger_entity (id, user_id),
    CONSTRAINT fk_account_institution FOREIGN KEY (institution_id, user_id)
        REFERENCES institution (id, user_id),
    CONSTRAINT fk_account_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connection (id, user_id),
    CONSTRAINT ck_account_type CHECK (account_type IN
        ('checking', 'savings', 'credit_card', 'brokerage', 'loan', 'cash')),
    CONSTRAINT ck_account_dates CHECK (closed_on IS NULL OR opened_on IS NULL OR closed_on >= opened_on),
    -- Scoped to the entity: Personal and Feeling Froggy may each hold a "Checking" without
    -- inventing disambiguating names.
    CONSTRAINT ux_account_name UNIQUE (user_id, ledger_entity_id, name),
    CONSTRAINT ux_account_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_account_user ON account (user_id) WHERE is_active;
CREATE INDEX ix_account_entity ON account (ledger_entity_id);
CREATE UNIQUE INDEX ux_account_external
    ON account (connection_id, external_id) WHERE external_id IS NOT NULL;
CREATE TRIGGER trg_account_updated BEFORE UPDATE ON account
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------------------------

-- One table replaces the legacy expenseCategory + incomeCategory, which were the same table twice.
-- parent_id supports grouping ("Utilities" → "Electric") without a later migration.
CREATE TABLE category (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    parent_id  BIGINT,
    name       VARCHAR(160) NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id, user_id)
        REFERENCES category (id, user_id),
    CONSTRAINT ck_category_kind CHECK (kind IN ('expense', 'income')),
    CONSTRAINT ck_category_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    -- Unique per PARENT, not globally: "Utilities → Electric" and "Home → Electric" coexist.
    -- NULLS NOT DISTINCT (PG15+) is required, or two top-level categories could share a name,
    -- because SQL otherwise treats NULL parent_ids as distinct from one another.
    CONSTRAINT ux_category_name UNIQUE NULLS NOT DISTINCT (user_id, kind, parent_id, name),
    CONSTRAINT ux_category_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_category_user ON category (user_id) WHERE is_active;
CREATE TRIGGER trg_category_updated BEFORE UPDATE ON category
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Import provenance (populated in M2; the FK exists now so transaction never needs altering)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE import_batch (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    connection_id   BIGINT,
    -- Nullable: a single OFX file can legitimately span several accounts.
    account_id      BIGINT,
    source_format   VARCHAR(20) NOT NULL,
    -- The file itself is NOT stored here. SECURITY.md: statement files are retained only as long
    -- as the import needs them, then deleted or moved to encrypted storage.
    original_filename VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    row_count       INTEGER     NOT NULL DEFAULT 0,
    applied_count   INTEGER     NOT NULL DEFAULT 0,
    duplicate_count INTEGER     NOT NULL DEFAULT 0,
    error           TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT fk_import_batch_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connection (id, user_id),
    CONSTRAINT fk_import_batch_account FOREIGN KEY (account_id, user_id)
        REFERENCES account (id, user_id),
    CONSTRAINT ck_import_batch_format
        CHECK (source_format IN ('csv', 'ofx', 'qfx', 'pdf', 'api', 'manual')),
    CONSTRAINT ck_import_batch_status
        CHECK (status IN ('pending', 'parsed', 'applied', 'failed')),
    CONSTRAINT ux_import_batch_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_import_batch_user ON import_batch (user_id, started_at DESC);


-- ---------------------------------------------------------------------------------------------
-- Transactions — the single primitive
-- ---------------------------------------------------------------------------------------------

-- Replaces the legacy expenseItem + incomeItem, which were the same shape with an opposite sign.
-- Amount is ALWAYS positive; `direction` carries the sign. This matches the AI service's
-- ParsedTransaction wire model exactly (services/ai/src/finances_ai/models.py) — changing one
-- without the other is a breaking change on both sides.
--
-- TRANSFERS ARE SINGLE-SIDED. One row is one account's view of a money movement, because that is
-- exactly what a statement reports. Importing both the checking and the card statement naturally
-- yields two rows — one per account, each with its own dedupe_key — and they net to zero across
-- the pair, so net worth stays correct without double-entry bookkeeping. `transfer_account_id` is
-- a LINK ("the other side is over there"), not a second ledger leg; `transfer_group_id` ties a
-- matched pair together once both sides are known.
CREATE TABLE transaction (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    account_id          BIGINT        NOT NULL,
    -- NULL means "inherit the account's entity". Populated only to override — the case that
    -- matters at tax time is a personal card carrying a business expense, and provenance cannot
    -- resolve that one. See docs/DOMAIN.md → Entity scoping.
    ledger_entity_id    BIGINT,
    -- NULL means uncategorized: either awaiting review, or a transfer (see the CHECK below).
    category_id         BIGINT,

    transaction_date    DATE          NOT NULL,
    posted_date         DATE,
    amount              NUMERIC(19,4) NOT NULL,
    direction           VARCHAR(10)   NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'USD',
    description         TEXT          NOT NULL,
    merchant            TEXT,

    -- THE credit-card double-count rule, and the single most important piece of business logic in
    -- this system. A payment to a card is not an expense: the budget was already charged when the
    -- purchase happened, so categorizing the payment too would count the spend twice.
    --
    -- The legacy schema expressed this with three booleans and a self-reference and enforced it by
    -- convention. Here it is one flag, one FK, and a CHECK — the database refuses to store the
    -- double-count. paidWithCredit is gone (implicit in account_type); interest payments are gone
    -- as a special case (interest is a real expense with a category — the legacy flag existed only
    -- to allocate it to a period, and periods no longer exist).
    is_transfer         BOOLEAN       NOT NULL DEFAULT FALSE,
    transfer_account_id BIGINT,
    transfer_group_id   UUID,

    -- Provenance and idempotency (D-14).
    source              VARCHAR(20)   NOT NULL DEFAULT 'manual',
    connection_id       BIGINT,
    import_batch_id     BIGINT,
    external_id         VARCHAR(255),
    -- Aggregators replace a pending row with a posted row carrying a DIFFERENT id, date and
    -- sometimes amount (tips, fuel holds). Without this link the dedupe key double-counts the
    -- pending→posted transition — the same class of bug as the credit-card double-count.
    pending             BOOLEAN       NOT NULL DEFAULT FALSE,
    pending_external_id VARCHAR(255),
    -- Stable hash from the parser; see finances_ai.ingest.csv_reader.dedupe_key.
    dedupe_key          VARCHAR(64)   NOT NULL,
    raw                 JSONB,

    -- Soft delete, deliberately. The dedupe index below spans deleted rows, so removing an
    -- imported transaction cannot be undone by re-importing the same statement. A hard DELETE
    -- would free the dedupe_key and silently resurrect the row on the next import.
    deleted_at          TIMESTAMPTZ,

    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id, user_id)
        REFERENCES account (id, user_id),
    CONSTRAINT fk_transaction_transfer_account FOREIGN KEY (transfer_account_id, user_id)
        REFERENCES account (id, user_id),
    CONSTRAINT fk_transaction_category FOREIGN KEY (category_id, user_id)
        REFERENCES category (id, user_id),
    CONSTRAINT fk_transaction_entity FOREIGN KEY (ledger_entity_id, user_id)
        REFERENCES ledger_entity (id, user_id),
    CONSTRAINT fk_transaction_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connection (id, user_id),
    CONSTRAINT fk_transaction_import_batch FOREIGN KEY (import_batch_id, user_id)
        REFERENCES import_batch (id, user_id),

    CONSTRAINT ck_transaction_direction CHECK (direction IN ('debit', 'credit')),
    CONSTRAINT ck_transaction_amount_positive CHECK (amount >= 0),
    CONSTRAINT ck_transaction_source
        CHECK (source IN ('manual', 'file_import', 'aggregator', 'business_system')),
    -- Enforces the double-count rule: a transfer is never categorized, and only a transfer names
    -- the other side of the move.
    CONSTRAINT ck_transaction_transfer_uncategorized
        CHECK (NOT is_transfer OR category_id IS NULL),
    CONSTRAINT ck_transaction_transfer_account
        CHECK (is_transfer OR (transfer_account_id IS NULL AND transfer_group_id IS NULL)),
    CONSTRAINT ck_transaction_no_self_transfer
        CHECK (transfer_account_id IS NULL OR transfer_account_id <> account_id)
    -- NOTE: there is deliberately NO "posted_date >= transaction_date" constraint. Institutions
    -- do report a post date before the transaction date on occasion (pre-authorization reversals,
    -- corrected postings). Rejecting those rows would fail an entire real statement import over a
    -- data-quality quirk. Anomalies belong in the M2 review queue, not in a hard constraint.
);

-- Idempotent re-import, guaranteed by the database rather than by application discipline:
-- re-importing an overlapping statement cannot create duplicates. Deliberately NOT filtered on
-- deleted_at — see the soft-delete note above.
CREATE UNIQUE INDEX ux_transaction_dedupe ON transaction (account_id, dedupe_key);
-- A provider's own id is authoritative when present.
CREATE UNIQUE INDEX ux_transaction_external
    ON transaction (connection_id, external_id) WHERE external_id IS NOT NULL;

CREATE INDEX ix_transaction_user_date
    ON transaction (user_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_transaction_account_date
    ON transaction (account_id, transaction_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_transaction_category
    ON transaction (category_id) WHERE category_id IS NOT NULL AND deleted_at IS NULL;
-- Drives the review queue: uncategorized, non-transfer, live rows.
CREATE INDEX ix_transaction_needs_review ON transaction (user_id, transaction_date DESC)
    WHERE category_id IS NULL AND NOT is_transfer AND deleted_at IS NULL;
CREATE INDEX ix_transaction_import_batch ON transaction (import_batch_id);
-- Matching a posted row back to the pending row it replaced (M5).
CREATE INDEX ix_transaction_pending_link
    ON transaction (account_id, pending_external_id) WHERE pending_external_id IS NOT NULL;
CREATE INDEX ix_transaction_transfer_group
    ON transaction (transfer_group_id) WHERE transfer_group_id IS NOT NULL;

CREATE TRIGGER trg_transaction_updated BEFORE UPDATE ON transaction
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Targets — effective-dated intent
-- ---------------------------------------------------------------------------------------------

-- Replaces budget + budget_expenseCategory + budget_incomeCategory + incomeItem.amountExpected.
--
-- A target is a number you set once that stays in effect until you change it. There are no
-- per-period rows to create and nothing to "close". On an income category it is a forecast; on an
-- expense category it is a budget — the same mechanism, because expected-vs-received and
-- budgeted-vs-spent are the same question.
--
-- Targets are OPTIONAL. Most categories should need none: baselines derived from the user's own
-- history (M3/M6) cover the common case at zero effort. A target exists to express intent that
-- contradicts history — "cut dining to $200" — which cannot be derived from the data.
CREATE TABLE target (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category_id      BIGINT        NOT NULL,
    -- NOT NULL: a budget always belongs to an entity. Personal and Feeling Froggy can each hold a
    -- distinct target for the same category, which the overlap constraint below permits precisely
    -- because the entity participates in it.
    ledger_entity_id BIGINT        NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    cadence          VARCHAR(20)   NOT NULL DEFAULT 'monthly',
    effective_from   DATE          NOT NULL,
    -- NULL means still in effect. Exclusive upper bound (see the exclusion constraint).
    effective_to     DATE,
    note             TEXT,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_target_category FOREIGN KEY (category_id, user_id)
        REFERENCES category (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_target_entity FOREIGN KEY (ledger_entity_id, user_id)
        REFERENCES ledger_entity (id, user_id),
    CONSTRAINT ck_target_cadence CHECK (cadence IN ('weekly', 'monthly', 'quarterly', 'yearly')),
    CONSTRAINT ck_target_amount CHECK (amount >= 0),
    CONSTRAINT ck_target_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
    -- Two contradictory targets for the same category AND entity on the same day is a data bug
    -- that would silently produce a wrong number in a report. Refuse it at the database.
    CONSTRAINT ex_target_no_overlap EXCLUDE USING gist (
        category_id WITH =,
        ledger_entity_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&
    )
);
CREATE INDEX ix_target_user ON target (user_id);
CREATE TRIGGER trg_target_updated BEFORE UPDATE ON target
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Statements — reconciliation checkpoints
-- ---------------------------------------------------------------------------------------------

-- Replaces the legacy accountPeriod. Same job — prove the ledger matches what the institution
-- says — but the cycle is no longer user-defined: it arrives inside the imported statement.
-- Statement cycles are frequently NOT calendar months (a card may close on the 18th), which is
-- exactly why conflating them with budgeting periods forced manual entry in the old model.
CREATE TABLE statement (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    account_id      BIGINT        NOT NULL,
    import_batch_id BIGINT,
    period_start    DATE          NOT NULL,
    period_end      DATE          NOT NULL,
    opening_balance NUMERIC(19,4),
    -- Signed per the SIGN CONVENTION at the top of this file: negative means owed.
    closing_balance NUMERIC(19,4) NOT NULL,
    -- Set once the computed balance has been checked against closing_balance.
    reconciled_at   TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_statement_account FOREIGN KEY (account_id, user_id)
        REFERENCES account (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_statement_import_batch FOREIGN KEY (import_batch_id, user_id)
        REFERENCES import_batch (id, user_id),
    CONSTRAINT ck_statement_period CHECK (period_end >= period_start),
    CONSTRAINT ux_statement_period UNIQUE (account_id, period_end)
);
CREATE INDEX ix_statement_account ON statement (account_id, period_end DESC);
CREATE TRIGGER trg_statement_updated BEFORE UPDATE ON statement
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------------------------
-- Categorization — suggestions and the corrections that train them
-- ---------------------------------------------------------------------------------------------

-- Applied in M3, created now because D-15 requires corrections to be captured from the start:
-- they are both the tier-2 similarity training signal and the only way M3 can MEASURE accuracy
-- rather than assert it. A correction that was applied but not recorded is a lost training example.
CREATE TABLE categorization (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id        BIGINT       NOT NULL REFERENCES transaction (id) ON DELETE CASCADE,
    suggested_category_id BIGINT       REFERENCES category (id),
    suggested_transfer    BOOLEAN      NOT NULL DEFAULT FALSE,
    confidence            NUMERIC(5,4) NOT NULL,
    method                VARCHAR(20)  NOT NULL,
    model_id              VARCHAR(80),
    rationale             TEXT,
    -- How a human resolved it. NULL while still only a suggestion.
    resolution            VARCHAR(20),
    resolved_category_id  BIGINT       REFERENCES category (id),
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_categorization_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_categorization_method CHECK (method IN ('rule', 'similarity', 'model')),
    CONSTRAINT ck_categorization_resolution
        CHECK (resolution IS NULL OR resolution IN ('accepted', 'corrected', 'rejected')),
    CONSTRAINT ck_categorization_resolved
        CHECK ((resolution IS NULL) = (resolved_at IS NULL)),
    -- A 'corrected' resolution must say what it was corrected TO, or the training signal is lost.
    CONSTRAINT ck_categorization_corrected_target
        CHECK (resolution <> 'corrected' OR resolved_category_id IS NOT NULL)
);
CREATE INDEX ix_categorization_transaction ON categorization (transaction_id);
-- At most one OPEN suggestion per transaction. Re-running the categorizer must replace the pending
-- suggestion rather than stack a second one, or the review queue shows duplicates and "the current
-- suggestion" becomes ambiguous. Resolved rows accumulate freely as history.
CREATE UNIQUE INDEX ux_categorization_open
    ON categorization (transaction_id) WHERE resolution IS NULL;
-- The training set: everything a human actually decided.
CREATE INDEX ix_categorization_resolved
    ON categorization (resolved_at DESC) WHERE resolution IS NOT NULL;


INSERT INTO schema_notes (note)
VALUES ('V2: rebuilt core domain. Periods/budgets removed, transaction is the single primitive. See docs/DOMAIN.md.');
