-- V12 — strategies and backtests (M7c, D-19).
--
-- A strategy is a named rule over bars: a kind the Python service knows, its parameters, one
-- symbol, one timeframe, and the number of shares a live signal proposes. "Active" means the API
-- asks the strategy for its opinion on a timer and turns a signal into a DRAFT order for a person
-- to confirm — the same pipeline as every other order. A strategy never trades by itself.
--
-- A backtest run is a claim kept whole: the request, its assumptions, the summary figures for a
-- list, and the full result the Python service returned (equity curve, trades, the warnings a
-- person is meant to read) as JSON text. It is history, and it is never updated.

CREATE TABLE strategy (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name              VARCHAR(80)   NOT NULL,
    kind              VARCHAR(40)   NOT NULL,
    params            TEXT          NOT NULL DEFAULT '{}',
    security_id       BIGINT        NOT NULL,
    timeframe         VARCHAR(8)    NOT NULL,
    quantity          NUMERIC(28,8) NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT false,
    notes             TEXT,
    last_evaluated_at TIMESTAMPTZ,
    last_evaluation   TEXT,
    last_signal_at    TIMESTAMPTZ,
    last_signal       TEXT,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_strategy_security FOREIGN KEY (security_id, user_id) REFERENCES security (id, user_id),
    CONSTRAINT ck_strategy_timeframe CHECK (timeframe IN ('1Min', '5Min', '15Min', '1Hour', '1Day')),
    CONSTRAINT ck_strategy_quantity CHECK (quantity > 0),
    CONSTRAINT ux_strategy_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_strategy_active ON strategy (user_id) WHERE active;
CREATE TRIGGER trg_strategy_updated BEFORE UPDATE ON strategy
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE backtest_run (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    strategy_id              BIGINT        REFERENCES strategy (id) ON DELETE SET NULL,
    kind                     VARCHAR(40)   NOT NULL,
    params                   TEXT          NOT NULL DEFAULT '{}',
    security_id              BIGINT        NOT NULL,
    timeframe                VARCHAR(8)    NOT NULL,
    start_date               DATE          NOT NULL,
    end_date                 DATE          NOT NULL,
    initial_cash             NUMERIC(19,4) NOT NULL,
    slippage_bps             NUMERIC(8,2)  NOT NULL,
    commission               NUMERIC(19,4) NOT NULL,
    out_of_sample_fraction   NUMERIC(4,2)  NOT NULL,
    provider                 VARCHAR(30)   NOT NULL,
    bars                     INTEGER       NOT NULL,
    trades                   INTEGER       NOT NULL,
    day_trades               INTEGER       NOT NULL,
    total_return_pct         NUMERIC(10,2) NOT NULL,
    benchmark_return_pct     NUMERIC(10,2) NOT NULL,
    max_drawdown_pct         NUMERIC(10,2) NOT NULL,
    sharpe                   NUMERIC(10,2),
    final_equity             NUMERIC(19,4) NOT NULL,
    in_sample_return_pct     NUMERIC(10,2),
    out_of_sample_return_pct NUMERIC(10,2),
    warnings                 TEXT,
    result                   TEXT          NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_backtest_security FOREIGN KEY (security_id, user_id) REFERENCES security (id, user_id),
    CONSTRAINT ux_backtest_run_tenant UNIQUE (id, user_id)
);
CREATE INDEX ix_backtest_run_user_time ON backtest_run (user_id, created_at DESC);
CREATE TRIGGER trg_backtest_run_updated BEFORE UPDATE ON backtest_run
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- An order a strategy proposed remembers which one, so the strategy can know whether it is long.
ALTER TABLE trade_order ADD COLUMN strategy_id BIGINT REFERENCES strategy (id) ON DELETE SET NULL;
CREATE INDEX ix_trade_order_strategy ON trade_order (strategy_id) WHERE strategy_id IS NOT NULL;

INSERT INTO schema_notes (note)
VALUES ('V12: strategy and backtest_run — rules over bars that propose drafts, and the honest record of how they did (M7c).');
