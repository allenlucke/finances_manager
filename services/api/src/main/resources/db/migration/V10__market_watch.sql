-- V10 — watching the market (M7a, D-18).
--
-- Four tables, and one rule that governs all of them: A PRICE IS NOT MONEY. Nothing here enters
-- the ledger, carries a direction, or changes a balance. A quote multiplies a share count into a
-- market value that is shown BESIDE the value the last positions file gave — labelled "at last
-- quote" — and never replaces it. The sign convention in V2 does not apply to anything in this
-- file.
--
--   watchlist    — symbols the person is watching that they may not hold.
--   quote        — a time series of observed prices per security. Append-only.
--   price_alert  — a rule on a security: above X, below X, or moved more than X% today.
--   alert_event  — every time an alert fired, and whether the notification was delivered.
--
-- Tenant integrity as everywhere: user_id on every row, composite FKs to (id, user_id).

CREATE TABLE watchlist (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    security_id BIGINT      NOT NULL,
    note        TEXT,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_watchlist_security FOREIGN KEY (security_id, user_id)
        REFERENCES security (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ux_watchlist_security UNIQUE (user_id, security_id),
    CONSTRAINT ux_watchlist_tenant UNIQUE (id, user_id)
);
CREATE TRIGGER trg_watchlist_updated BEFORE UPDATE ON watchlist
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- An observation, not a fact about the ledger. Immutable once written, so no version and no
-- updated_at. Prices keep six decimals: a quote is finer than a ledger amount, and the same
-- NUMERIC(19,6) holding.last_price uses.
CREATE TABLE quote (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    security_id    BIGINT        NOT NULL,
    -- The vendor's own timestamp for the trade, not when this row was written.
    as_of          TIMESTAMPTZ   NOT NULL,
    price          NUMERIC(19,6) NOT NULL,
    previous_close NUMERIC(19,6),
    -- "alpaca", "fake". Shown, so a fake price can never be mistaken for a real one.
    source         VARCHAR(30)   NOT NULL,
    fetched_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_quote_security FOREIGN KEY (security_id, user_id)
        REFERENCES security (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_quote_price CHECK (price > 0),
    -- Refreshing between trades re-reads the same last trade; one row per moment, not per poll.
    CONSTRAINT ux_quote_moment UNIQUE (security_id, as_of),
    CONSTRAINT ux_quote_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_quote_security_time ON quote (security_id, as_of DESC);


-- `armed` is the edge detector. An alert fires when its condition becomes true while armed, then
-- disarms; it re-arms when the condition is false again. Without it "AAPL above 190" would fire on
-- every five-minute poll for as long as AAPL stayed above 190. `reference_close` lets a
-- percent-move alert re-arm each trading day, since its condition is measured against that day's
-- previous close.
CREATE TABLE price_alert (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    security_id     BIGINT        NOT NULL,
    rule            VARCHAR(20)   NOT NULL,
    threshold       NUMERIC(19,6) NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    armed           BOOLEAN       NOT NULL DEFAULT TRUE,
    last_fired_at   TIMESTAMPTZ,
    reference_close NUMERIC(19,6),
    note            TEXT,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_price_alert_security FOREIGN KEY (security_id, user_id)
        REFERENCES security (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_price_alert_rule CHECK (rule IN ('above', 'below', 'pct_move')),
    CONSTRAINT ck_price_alert_threshold CHECK (threshold > 0),
    CONSTRAINT ux_price_alert_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_price_alert_user ON price_alert (user_id) WHERE active;
CREATE TRIGGER trg_price_alert_updated BEFORE UPDATE ON price_alert
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- The record of a firing. `delivered` is what the notification channel said, kept separately from
-- the firing itself: an alert that fired and could not be delivered is still an alert that fired,
-- and the screen shows both.
CREATE TABLE alert_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    alert_id       BIGINT      NOT NULL,
    quote_id       BIGINT,
    fired_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    message        TEXT        NOT NULL,
    delivered      BOOLEAN     NOT NULL DEFAULT FALSE,
    delivery_error TEXT,
    CONSTRAINT fk_alert_event_alert FOREIGN KEY (alert_id, user_id)
        REFERENCES price_alert (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_event_quote FOREIGN KEY (quote_id, user_id)
        REFERENCES quote (id, user_id) ON DELETE SET NULL,
    CONSTRAINT ux_alert_event_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_alert_event_user_time ON alert_event (user_id, fired_at DESC);

INSERT INTO schema_notes (note)
VALUES ('V10: watchlist, quote, price_alert, alert_event — watching the market (M7a). A price is not money.');
