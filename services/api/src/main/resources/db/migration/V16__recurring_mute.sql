-- V16 — "this is not a recurring charge" (M9).
--
-- The detector finds series; the person knows better about some of them (a coincidence of three
-- coffees on Fridays). A mute is remembered by the series key, so the same coincidence does not
-- come back next week, and is shown as muted rather than made to disappear.

CREATE TABLE recurring_mute (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    series_key VARCHAR(240) NOT NULL,
    label      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_recurring_mute UNIQUE (user_id, series_key)
);

INSERT INTO schema_notes (note)
VALUES ('V16: recurring_mute — a series the person says is not recurring, remembered by key (M9).');
