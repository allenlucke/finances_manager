-- V4 — make the import link actually unique.
--
-- V2 indexed (connection_id, external_id). That was written when external_id meant "the
-- aggregator's id for this account", so scoping it to a connection made sense. It no longer does:
-- external_id now also holds the link an *import* derives from the account number in a statement
-- file, and those accounts have no connection at all.
--
-- In PostgreSQL a unique index containing a NULL column constrains nothing, because NULLs are
-- never equal to each other. So every imported account had connection_id NULL and the index
-- silently permitted unlimited duplicates. Verified, not assumed: two accounts were inserted with
-- an identical external_id and both were accepted.
--
-- The consequence was not cosmetic. Clicking "Create these accounts and import" twice produced two
-- accounts sharing one link, after which the lookup that resolves a row to its account matches two
-- rows and the import fails outright.
--
-- The link is a property of the user's account, not of a connection, so that is how it is scoped.

DROP INDEX IF EXISTS ux_account_external;

CREATE UNIQUE INDEX ux_account_link
    ON account (user_id, external_id)
    WHERE external_id IS NOT NULL;

-- The same reasoning for transactions. A provider's transaction id is unique within an account,
-- and imported rows likewise carry no connection. Dedupe is already enforced by
-- ux_transaction_dedupe, which now derives from this id where one exists — this makes the
-- guarantee explicit rather than a consequence of how the key happens to be built today.
DROP INDEX IF EXISTS ux_transaction_external;

CREATE UNIQUE INDEX ux_transaction_external
    ON transaction (account_id, external_id)
    WHERE external_id IS NOT NULL;

INSERT INTO schema_notes (note)
VALUES ('V4: import links are unique per user/account. The V2 indexes included a nullable column and so constrained nothing.');
