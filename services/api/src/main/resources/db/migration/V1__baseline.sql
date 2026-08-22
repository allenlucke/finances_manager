-- V1 — baseline.
--
-- Deliberately minimal. The real schema arrives in M1 (see docs/ROADMAP.md), ported from the 2021
-- model in legacy/db/pgInit.sql with the changes described in docs/DOMAIN.md:
--   * snake_case, unquoted identifiers (the legacy schema used quoted camelCase)
--   * money as NUMERIC(19,4), never float
--   * every timestamp TIMESTAMPTZ, stored UTC
--   * new: institution/connection, transaction, categorization, holding, entity scoping
--
-- Migrations are forward-only. Never edit a file that has been applied — add a new one.

CREATE TABLE schema_notes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    note         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_notes (note)
VALUES ('M0 baseline. Real domain schema lands in M1 — see docs/DOMAIN.md.');
