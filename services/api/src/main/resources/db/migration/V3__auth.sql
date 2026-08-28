-- V3 — authentication storage (D-12: self-hosted, session cookies + WebAuthn passkeys).
--
-- Three groups of tables, and only the first follows this project's naming conventions:
--
--   1. login_attempt  — ours. snake_case, FK to app_user.
--   2. SPRING_SESSION*— Spring Session JDBC's own schema, copied verbatim from
--                       spring-session-jdbc-4.1.1.jar!/org/springframework/session/jdbc/
--                       schema-postgresql.sql. DO NOT rename or restyle these: Spring Session's
--                       JdbcIndexedSessionRepository issues SQL against these exact identifiers.
--   3. user_entities / user_credentials
--                     — Spring Security's WebAuthn JDBC schema. Column names and types are
--                       dictated by JdbcPublicKeyCredentialUserEntityRepository and
--                       JdbcUserCredentialRepository. Same rule: do not restyle.
--
-- Using Spring's own JDBC repositories rather than hand-rolling persistence is deliberate.
-- Credential storage is a security-critical path and a subtle mapping bug there breaks
-- authentication in ways that are hard to see. The cost is two tables that do not match our
-- conventions, which is a fair trade and is why this comment exists.
--
-- Schema initialization is OWNED BY FLYWAY: spring.session.jdbc.initialize-schema=never.

-- ---------------------------------------------------------------------------------------------
-- Spring Session JDBC (verbatim — see note above)
-- ---------------------------------------------------------------------------------------------

-- Sessions live in Postgres, not Tomcat memory. D-12 chose sessions over JWTs precisely for
-- instant revocation; persisting them means "log out everywhere" is a DELETE, and a deploy or
-- reboot does not silently sign you out.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------------------------
-- WebAuthn / passkeys (column names and types dictated by Spring Security — see note above)
-- ---------------------------------------------------------------------------------------------

-- The WebAuthn user handle. `name` is the username, which in this system is app_user.email.
-- A foreign key to app_user is not possible: Spring Security's INSERT writes only
-- (id, name, display_name), so any extra NOT NULL column would break it. The linkage is by
-- username, enforced case-insensitively to match ux_app_user_email.
CREATE TABLE user_entities (
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(320)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ux_user_entities_name ON user_entities (lower(name));

-- One registered passkey. public_key / attestation_* are BYTEA because the repository reads and
-- writes them as raw bytes; the remaining types follow its RowMapper exactly
-- (getString / getLong / getBoolean / getTimestamp).
--
-- The ids are Base64URL text, not raw bytes: Spring Security encodes its Bytes type on the way in
-- and decodes on the way out. Writing plain text into them fails at read with
-- "Last unit does not have enough valid bits".
-- NOT NULL on more columns than Spring's reference schema marks, and the reason is not stylistic:
-- its CredentialRecordRowMapper calls String.split() on authenticator_transports, reads the flags
-- as primitives, and explicitly rejects a null last_used. A NULL in any of them is not a missing
-- value, it is an exception thrown inside the authorization filter — which fails every request,
-- not one. Spring's published reference schema is looser than its own mapper actually tolerates;
-- these constraints encode what the code requires. Defaults keep its repository's inserts working.
CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000) NOT NULL,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BYTEA         NOT NULL,
    signature_count              BIGINT        NOT NULL DEFAULT 0,
    uv_initialized               BOOLEAN       NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN       NOT NULL DEFAULT FALSE,
    authenticator_transports     VARCHAR(1000) NOT NULL DEFAULT '',
    public_key_credential_type   VARCHAR(100)  NOT NULL DEFAULT 'public-key',
    backup_state                 BOOLEAN       NOT NULL DEFAULT FALSE,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Also NOT NULL: the row mapper rejects a null outright ("last_used cannot be null").
    last_used                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    label                        VARCHAR(255)  NOT NULL,
    PRIMARY KEY (credential_id),
    CONSTRAINT fk_user_credentials_entity FOREIGN KEY (user_entity_user_id)
        REFERENCES user_entities (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_credentials_user ON user_credentials (user_entity_user_id);


-- ---------------------------------------------------------------------------------------------
-- Login attempts (ours)
-- ---------------------------------------------------------------------------------------------

-- Lockout and audit. SECURITY.md forbids logging account identifiers at INFO, so failed logins are
-- recorded here rather than in the application log, where they would be both noisier and leakier.
--
-- username is stored, not user_id: the interesting failures are the ones against accounts that do
-- not exist. It is deliberately NOT a foreign key.
CREATE TABLE login_attempt (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username    VARCHAR(320) NOT NULL,
    successful  BOOLEAN      NOT NULL,
    method      VARCHAR(20)  NOT NULL,
    -- Nullable: absent behind a reverse proxy that does not forward it.
    source_ip   INET,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_login_attempt_method CHECK (method IN ('password', 'passkey'))
);
-- Drives lockout: "how many failures for this username recently".
CREATE INDEX ix_login_attempt_recent
    ON login_attempt (lower(username), attempted_at DESC) WHERE NOT successful;


INSERT INTO schema_notes (note)
VALUES ('V3: auth. Spring Session JDBC + WebAuthn passkey storage + login attempt audit. See D-12.');
