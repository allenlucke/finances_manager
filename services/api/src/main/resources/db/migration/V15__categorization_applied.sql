-- V15 — categorization, wired (M3a, D-22).
--
-- A suggestion confident enough is applied without waiting for a person; that is a fourth way a
-- suggestion can end, distinct from a person accepting it, and the review screen and the accuracy
-- figure both need to tell the two apart. A rule may also name a category the person does not
-- have yet ("Subscriptions"); the name is kept so the screen can offer to create it, rather than
-- the suggestion being dropped or a category being invented behind the person's back.

ALTER TABLE categorization DROP CONSTRAINT ck_categorization_resolution;
ALTER TABLE categorization ADD CONSTRAINT ck_categorization_resolution
    CHECK (resolution IS NULL OR resolution IN ('accepted', 'corrected', 'rejected', 'applied'));
ALTER TABLE categorization ADD COLUMN suggested_name VARCHAR(160);

INSERT INTO schema_notes (note)
VALUES ('V15: categorization gains the applied resolution and suggested_name (M3a).');

-- What the import did about categories, beside what it did about rows. Counts, not notes: the
-- warnings column is the parser's, and a sentence there would be counted as a problem.
ALTER TABLE import_batch ADD COLUMN auto_categorized INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batch ADD COLUMN suggested INTEGER NOT NULL DEFAULT 0;
