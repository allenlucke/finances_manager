-- V9 — the file's own word for a row.
--
-- Chase writes "Sale", "Payment", "Return"; OFX writes "XFER"; a brokerage history writes the
-- action. It is what the parser's transfer and refund hints were read from, and it was thrown away
-- at import: the API kept the hint and lost the word. Kept now so the review queue can say "file
-- says Return" beside a credit, and so M3's categorizer has the institution's own classification
-- as a tier-1 feature rather than an inference over the description.
--
-- Nullable: a manual entry has none, and neither does a generic CSV without a type column.
ALTER TABLE transaction ADD COLUMN source_type VARCHAR(40);

INSERT INTO schema_notes (note)
VALUES ('V9: transaction.source_type — the source file''s own row type, for the review queue and M3.');
