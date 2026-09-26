-- V13 — the app speaks up (M8, D-20).
--
-- Until now the app only spoke when a price crossed a line. A reminder is a dated thing the
-- person asked to be told about — estimated taxes, an LLC filing, a bill — once or on a cadence.
-- A digest is what the app noticed on its own: an account nobody has imported for a month, a
-- statement the ledger disagrees with, a category over its target, an order waiting on a
-- decision, a reminder coming due. It is composed on request for the dashboard and pushed once a
-- day; what was pushed is kept, like an alert firing, whether or not it was delivered.

CREATE TABLE reminder (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title        VARCHAR(120)  NOT NULL,
    notes        TEXT,
    due_on       DATE          NOT NULL,
    cadence      VARCHAR(10)   NOT NULL DEFAULT 'once',
    lead_days    INTEGER       NOT NULL DEFAULT 3,
    amount       NUMERIC(19,4),
    active       BOOLEAN       NOT NULL DEFAULT true,
    last_done_on DATE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_reminder_cadence CHECK (cadence IN ('once', 'weekly', 'monthly', 'quarterly', 'yearly')),
    CONSTRAINT ck_reminder_lead CHECK (lead_days >= 0 AND lead_days <= 90),
    CONSTRAINT ux_reminder_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_reminder_due ON reminder (user_id, due_on) WHERE active;
CREATE TRIGGER trg_reminder_updated BEFORE UPDATE ON reminder
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- One row per person: when the digest goes out and what counts as stale.
CREATE TABLE notice_preference (
    user_id          BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    digest_enabled   BOOLEAN     NOT NULL DEFAULT true,
    digest_time      TIME        NOT NULL DEFAULT '07:30',
    stale_after_days INTEGER     NOT NULL DEFAULT 35,
    draft_wait_hours INTEGER     NOT NULL DEFAULT 24,
    quiet_when_empty BOOLEAN     NOT NULL DEFAULT true,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notice_stale CHECK (stale_after_days BETWEEN 1 AND 365),
    CONSTRAINT ck_notice_wait CHECK (draft_wait_hours BETWEEN 1 AND 720)
);
CREATE TRIGGER trg_notice_preference_updated BEFORE UPDATE ON notice_preference
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- What was said, and whether it got through.
CREATE TABLE digest_run (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    for_date       DATE        NOT NULL,
    ran_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    manual         BOOLEAN     NOT NULL DEFAULT false,
    items          INTEGER     NOT NULL,
    sent           BOOLEAN     NOT NULL,
    delivery_error TEXT,
    body           TEXT        NOT NULL
);
CREATE INDEX ix_digest_run_user_date ON digest_run (user_id, for_date DESC, manual);

INSERT INTO schema_notes (note)
VALUES ('V13: reminder, notice_preference, digest_run — the app speaks up once a day about what needs a look (M8).');
