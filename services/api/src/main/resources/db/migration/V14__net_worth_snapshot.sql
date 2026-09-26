-- V14 — net worth over time (M9, D-21).
--
-- v_net_worth is always "now"; nothing kept yesterday's figure, so there was no trend to show.
-- One row per person per day per set of books (NULL = combined), written by the housekeeping
-- tick and on request, and re-written if taken twice in a day. History, never a balance: the
-- ledger stays the source and this is what it said on each date.

CREATE TABLE net_worth_snapshot (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    as_of             DATE          NOT NULL,
    ledger_entity_id  BIGINT,
    net_worth         NUMERIC(19,4) NOT NULL,
    snapshot_accounts INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_net_worth_snapshot UNIQUE NULLS NOT DISTINCT (user_id, as_of, ledger_entity_id)
);
CREATE INDEX ix_net_worth_snapshot_user ON net_worth_snapshot (user_id, as_of);

INSERT INTO schema_notes (note)
VALUES ('V14: net_worth_snapshot — what net worth was on each date, so a trend exists (M9).');
