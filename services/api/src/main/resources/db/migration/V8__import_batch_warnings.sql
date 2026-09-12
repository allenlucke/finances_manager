-- V8 — what the parser said about a file, kept with the batch.
--
-- The parser reports per-row warnings ("line 3: Unrecognized date …", the Windows-1252 notice, a
-- multi-statement OFX) and the API counted them into a log line and dropped them. So CLAUDE.md's
-- "a row with a date is always attempted and its failure reported with a line number" was reported
-- to a log counter, and a file that lost rows to a bad date looked identical to one that did not.
-- Review 2026-09-11, J1.
--
-- TEXT with one warning per line rather than JSONB: it is read back only to be shown, and a plain
-- column needs no custom Hibernate type. The API bounds it before writing.
ALTER TABLE import_batch ADD COLUMN warnings TEXT;

INSERT INTO schema_notes (note)
VALUES ('V8: import_batch.warnings — the parser''s per-row notes, shown rather than counted. Review 2026-09-11 J1.');
