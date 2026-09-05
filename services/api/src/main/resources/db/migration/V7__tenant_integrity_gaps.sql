-- V7 — close the three places that broke the schema's own tenant rule (review B13).
--
-- V2's header: every scoped table carries user_id AND a composite FK (child_id, user_id) →
-- (id, user_id), so a cross-user reference is unrepresentable rather than merely unwritten.
-- `categorization` was built without it — single-column FKs to transaction and category, and no
-- user_id at all — which is exactly what that header calls "expensive to retrofit once data
-- exists". Nothing writes to the table yet (M3 does), so this is the cheap moment.
--
-- The target overlap exclusion likewise excluded on (category, entity, range) with no user, and
-- was safe only because category ids happen to be globally unique. Stated now.

-- transaction never had the composite target the rule requires of every parent.
ALTER TABLE transaction ADD CONSTRAINT ux_transaction_tenant UNIQUE (id, user_id);

ALTER TABLE categorization ADD COLUMN user_id BIGINT;
UPDATE categorization c SET user_id = t.user_id FROM transaction t WHERE t.id = c.transaction_id;
ALTER TABLE categorization ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE categorization
    ADD CONSTRAINT fk_categorization_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_categorization_transaction_tenant FOREIGN KEY (transaction_id, user_id)
        REFERENCES transaction (id, user_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_categorization_suggested_tenant FOREIGN KEY (suggested_category_id, user_id)
        REFERENCES category (id, user_id),
    ADD CONSTRAINT fk_categorization_resolved_tenant FOREIGN KEY (resolved_category_id, user_id)
        REFERENCES category (id, user_id);
CREATE INDEX ix_categorization_user ON categorization (user_id);

ALTER TABLE target DROP CONSTRAINT ex_target_no_overlap;
ALTER TABLE target ADD CONSTRAINT ex_target_no_overlap EXCLUDE USING gist (
    user_id          WITH =,
    category_id      WITH =,
    ledger_entity_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
);

INSERT INTO schema_notes (note)
VALUES ('V7: tenant integrity on categorization and the target overlap rule. Review B13.');
