CREATE TABLE merchant_accounts (
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ
);

INSERT INTO merchant_accounts (id, name, created_at) VALUES ('acct_default', 'default', NOW());

CREATE TABLE principals (
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    account_id TEXT        NOT NULL REFERENCES merchant_accounts (id),
    CONSTRAINT principals_account_name_key UNIQUE (account_id, name),
    CONSTRAINT principals_id_account_key UNIQUE (id, account_id)
);

CREATE TABLE api_keys (
    id            TEXT PRIMARY KEY,
    principal_id  TEXT        NOT NULL,
    lookup_prefix TEXT        NOT NULL,
    digest        BYTEA       NOT NULL,
    scopes        TEXT        NOT NULL,
    environment   TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    account_id    TEXT        NOT NULL,
    CONSTRAINT api_keys_principal_account_fkey FOREIGN KEY (principal_id, account_id) REFERENCES principals (id, account_id)
);

CREATE INDEX api_keys_lookup ON api_keys (lookup_prefix);

CREATE TABLE admin_sessions (
    id         TEXT PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX admin_sessions_expires_at ON admin_sessions (expires_at);

CREATE TABLE provider_credential_revisions (
    id         TEXT        PRIMARY KEY,
    account_id TEXT        NOT NULL REFERENCES merchant_accounts (id),
    provider   TEXT        NOT NULL,
    status     TEXT        NOT NULL CHECK (status IN ('active', 'retired', 'revoked')),
    names      TEXT        NOT NULL,
    key_id     TEXT,
    nonce      BYTEA,
    ciphertext BYTEA,
    created_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    UNIQUE (id, account_id, provider),
    CHECK ((key_id IS NULL) = (nonce IS NULL) AND (nonce IS NULL) = (ciphertext IS NULL)),
    CHECK (status <> 'active' OR (key_id IS NOT NULL AND retired_at IS NULL AND revoked_at IS NULL)),
    CHECK (status <> 'retired' OR (retired_at IS NOT NULL AND revoked_at IS NULL)),
    CHECK (status <> 'revoked' OR (revoked_at IS NOT NULL AND key_id IS NULL))
);

CREATE UNIQUE INDEX provider_credential_revisions_active ON provider_credential_revisions (account_id, provider) WHERE status = 'active';
CREATE INDEX provider_credential_revisions_retired ON provider_credential_revisions (retired_at) WHERE status = 'retired' AND key_id IS NOT NULL;

CREATE TABLE provider_credential_modes (
    account_id TEXT        NOT NULL REFERENCES merchant_accounts (id),
    provider   TEXT        NOT NULL,
    mode       TEXT        NOT NULL CHECK (mode IN ('platform', 'own')),
    changed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, provider)
);

CREATE TABLE payments (
    id                       TEXT PRIMARY KEY,
    reference                TEXT        NOT NULL,
    status                   TEXT        NOT NULL,
    amount                   BIGINT      NOT NULL,
    currency                 TEXT        NOT NULL,
    provider                 TEXT        NOT NULL,
    provider_reference       TEXT,
    provider_order_id        TEXT        NOT NULL,
    payer_phone_number       TEXT,
    description              TEXT,
    return_url               TEXT,
    next_action              TEXT,
    metadata                 TEXT,
    expires_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    failure_reason           TEXT,
    failure_detail           TEXT,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    callback_token_digest    BYTEA UNIQUE,
    creation_outcome_unknown BOOLEAN     NOT NULL DEFAULT FALSE,
    locale                   TEXT,
    creation_principal       TEXT,
    idempotency_key          TEXT,
    request_fingerprint      TEXT,
    version                  BIGINT      NOT NULL DEFAULT 0,
    fee_amount               BIGINT CHECK (fee_amount >= 0),
    fee_bearer               TEXT CHECK (fee_bearer IN ('merchant', 'payer')),
    account_id               TEXT        NOT NULL REFERENCES merchant_accounts (id),
    reconciliation_attempts  INTEGER     NOT NULL DEFAULT 0,
    next_check_at            TIMESTAMPTZ,
    booked_at                TIMESTAMPTZ,
    credential_revision      TEXT,
    CONSTRAINT payments_account_reference_key UNIQUE (account_id, reference),
    CONSTRAINT payments_credential_revision_fkey
        FOREIGN KEY (credential_revision, account_id, provider) REFERENCES provider_credential_revisions (id, account_id, provider),
    CONSTRAINT payments_terminal_fields CHECK (
        (status = 'pending' AND completed_at IS NULL)
        OR (status <> 'pending' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT payments_failure_reason CHECK ((status = 'failed') = (failure_reason IS NOT NULL)),
    CONSTRAINT payments_fee_complete CHECK ((fee_amount IS NULL) = (fee_bearer IS NULL))
);

CREATE UNIQUE INDEX payments_provider_order ON payments (provider, provider_order_id);
CREATE INDEX payments_pending_credential_revision ON payments (credential_revision) WHERE status = 'pending' AND credential_revision IS NOT NULL;
CREATE INDEX payments_reconciliation_turn ON payments (COALESCE(next_check_at, updated_at)) WHERE status = 'pending';
CREATE INDEX payments_statement ON payments (provider, booked_at, id) WHERE status = 'succeeded';

CREATE TABLE payment_transitions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id  TEXT        NOT NULL REFERENCES payments (id),
    from_status TEXT,
    to_status   TEXT        NOT NULL,
    source      TEXT        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX payment_transitions_payment ON payment_transitions (payment_id, id);

CREATE TABLE payment_conflicts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id  TEXT        NOT NULL REFERENCES payments (id),
    source      TEXT        NOT NULL,
    reported    TEXT        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE indeterminate_operations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id  TEXT        NOT NULL REFERENCES payments (id),
    operation   TEXT        NOT NULL,
    error       TEXT        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE attestations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id     TEXT        NOT NULL REFERENCES payments (id),
    person         TEXT        NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('succeeded', 'failed', 'expired', 'canceled')),
    failure_reason TEXT,
    evidence       TEXT        NOT NULL,
    recorded_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_records (
    principal        TEXT        NOT NULL,
    operation        TEXT        NOT NULL,
    idempotency_key  TEXT        NOT NULL,
    fingerprint      TEXT        NOT NULL,
    response_status  INTEGER,
    response_body    BYTEA,
    created_at       TIMESTAMPTZ NOT NULL,
    claimed_at       TIMESTAMPTZ NOT NULL,
    claim_generation BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (principal, operation, idempotency_key)
);

CREATE INDEX idempotency_records_created_at ON idempotency_records (created_at);

CREATE TABLE events (
    id              TEXT PRIMARY KEY,
    payment_id      TEXT        NOT NULL REFERENCES payments (id),
    type            TEXT        NOT NULL,
    sequence        INTEGER     NOT NULL,
    previous_status TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    body            BYTEA       NOT NULL,
    UNIQUE (payment_id, sequence)
);

CREATE TABLE webhook_endpoints (
    name       TEXT PRIMARY KEY,
    url        TEXT        NOT NULL,
    events     TEXT        NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    account_id TEXT        NOT NULL REFERENCES merchant_accounts (id)
);

CREATE TABLE webhook_endpoint_state (
    endpoint              TEXT PRIMARY KEY,
    consecutive_abandoned INTEGER NOT NULL DEFAULT 0,
    disabled_at           TIMESTAMPTZ,
    reason                TEXT
);

CREATE TABLE webhook_deliveries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        TEXT        NOT NULL REFERENCES events (id),
    endpoint        TEXT        NOT NULL,
    url             TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('pending', 'succeeded', 'abandoned', 'stopped')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL,
    UNIQUE (event_id, endpoint)
);

CREATE INDEX webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'pending';

CREATE TABLE webhook_attempts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id BIGINT      NOT NULL REFERENCES webhook_deliveries (id),
    attempt     INTEGER     NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    status_code INTEGER,
    error       TEXT,
    duration_ms INTEGER     NOT NULL
);

CREATE INDEX webhook_attempts_delivery ON webhook_attempts (delivery_id, attempt);

CREATE TABLE webhook_nonces (
    nonce      TEXT PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX webhook_nonces_expires_at ON webhook_nonces (expires_at);

CREATE TABLE webhook_signing_keys (
    kid                    TEXT PRIMARY KEY,
    public_key             BYTEA       NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    activates_at           TIMESTAMPTZ NOT NULL,
    retired_at             TIMESTAMPTZ,
    sealing_key_id         TEXT        NOT NULL,
    private_key_nonce      BYTEA       NOT NULL,
    private_key_ciphertext BYTEA       NOT NULL
);

CREATE TABLE audit_records (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recorded_at      TIMESTAMPTZ NOT NULL,
    kind             TEXT        NOT NULL,
    principal_id     TEXT,
    lookup_prefix    TEXT,
    operation        TEXT        NOT NULL,
    request_id       TEXT,
    result           TEXT        NOT NULL,
    detail           TEXT,
    remote_address   TEXT,
    duration_ms      BIGINT,
    request_headers  JSONB,
    request_body     TEXT,
    response_headers JSONB,
    response_body    TEXT
);

CREATE INDEX audit_records_recorded ON audit_records (recorded_at DESC);
