-- V11 — orders (M7b, D-18).
--
-- Order execution is a different risk class from everything else in this system: reading a
-- balance wrong shows a bad number, sending an order wrong loses money irreversibly. So an order
-- has a life the database can see — draft, confirmed, submitted, accepted, filled — and every step
-- of it is an event with an actor. Nothing here is a ledger row. A fill changes what is owned;
-- what is owned is learned from the next positions import, as it always was.
--
-- `venue` says where the order goes: `paper`, Alpaca's paper account through the Python service,
-- or `manual`, a ticket the person carries to Fidelity by hand and marks placed afterwards.
-- `client_order_id` is ours and unique, and travels to the broker, so a retry after a timeout
-- cannot place the same order twice.

CREATE TABLE trade_order (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    security_id       BIGINT        NOT NULL,
    venue             VARCHAR(20)   NOT NULL,
    side              VARCHAR(4)    NOT NULL,
    quantity          NUMERIC(28,8) NOT NULL,
    order_type        VARCHAR(10)   NOT NULL DEFAULT 'market',
    limit_price       NUMERIC(19,6),
    time_in_force     VARCHAR(5)    NOT NULL DEFAULT 'day',
    status            VARCHAR(20)   NOT NULL DEFAULT 'draft',
    proposed_by       VARCHAR(10)   NOT NULL DEFAULT 'person',
    rationale         TEXT,
    -- The quote the draft was sized against, and quantity × (limit or that quote): what the
    -- daily cap is measured in and what the person confirms.
    reference_price   NUMERIC(19,6),
    notional_estimate NUMERIC(19,4) NOT NULL,
    client_order_id   VARCHAR(48)   NOT NULL,
    broker            VARCHAR(30),
    broker_order_id   VARCHAR(100),
    broker_status     VARCHAR(40),
    filled_quantity   NUMERIC(28,8) NOT NULL DEFAULT 0,
    filled_avg_price  NUMERIC(19,6),
    confirmed_at      TIMESTAMPTZ,
    submitted_at      TIMESTAMPTZ,
    filled_at         TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    last_error        TEXT,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_trade_order_security FOREIGN KEY (security_id, user_id)
        REFERENCES security (id, user_id),
    CONSTRAINT ck_trade_order_venue CHECK (venue IN ('paper', 'manual')),
    CONSTRAINT ck_trade_order_side CHECK (side IN ('buy', 'sell')),
    CONSTRAINT ck_trade_order_quantity CHECK (quantity > 0),
    CONSTRAINT ck_trade_order_type CHECK (order_type IN ('market', 'limit')),
    CONSTRAINT ck_trade_order_limit CHECK (order_type <> 'limit' OR limit_price IS NOT NULL),
    CONSTRAINT ck_trade_order_tif CHECK (time_in_force IN ('day', 'gtc')),
    CONSTRAINT ck_trade_order_status CHECK (status IN (
        'draft', 'confirmed', 'submitted', 'accepted', 'partially_filled', 'filled',
        'placed_manually', 'cancelled', 'rejected', 'expired', 'failed')),
    CONSTRAINT ck_trade_order_proposer CHECK (proposed_by IN ('person', 'assistant')),
    CONSTRAINT ux_trade_order_client UNIQUE (client_order_id),
    CONSTRAINT ux_trade_order_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_trade_order_user_time ON trade_order (user_id, created_at DESC);
CREATE INDEX ix_trade_order_open ON trade_order (user_id)
    WHERE status IN ('submitted', 'accepted', 'partially_filled');
CREATE TRIGGER trg_trade_order_updated BEFORE UPDATE ON trade_order
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Who moved the order from what to what, and why. The audit trail an order needs and a ledger row
-- never had: "the assistant proposed, the person confirmed, the broker filled".
CREATE TABLE trade_order_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    order_id    BIGINT      NOT NULL,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    actor       VARCHAR(10) NOT NULL,
    note        TEXT,
    CONSTRAINT fk_trade_order_event_order FOREIGN KEY (order_id, user_id)
        REFERENCES trade_order (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_trade_order_event_actor CHECK (actor IN ('person', 'assistant', 'system', 'broker')),
    CONSTRAINT ux_trade_order_event_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_trade_order_event_order ON trade_order_event (order_id, at);

INSERT INTO schema_notes (note)
VALUES ('V11: trade_order and trade_order_event — propose, confirm, execute, with an audit trail (M7b).');
