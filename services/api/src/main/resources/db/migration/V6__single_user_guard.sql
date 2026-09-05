-- V6 — make "exactly one user" a database fact rather than a check-then-act in a controller.
--
-- SetupController refuses to create a second account once one exists, but it does so by reading
-- a count and then inserting, at READ COMMITTED. Two concurrent first-run requests with different
-- addresses both see zero and both insert. The blast radius is larger than an extra row: the local
-- automation token (D-17) refuses to authenticate at all unless there is exactly one user, so a
-- second row silently disables Claude Code's access, and the symptom is a 401 that looks exactly
-- like a wrong token.
--
-- A unique index over a constant admits at most one row. This is deliberately a hard statement
-- that the system is single-user: if that ever changes, dropping this index is a one-line
-- migration, and the design conversation it forces is the point.
CREATE UNIQUE INDEX ux_app_user_single ON app_user ((true));

INSERT INTO schema_notes (note)
VALUES ('V6: at most one app_user, enforced by the database. See docs/REVIEW-2026-08-29.md B10.');
