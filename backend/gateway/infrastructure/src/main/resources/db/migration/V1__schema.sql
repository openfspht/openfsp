create table merchant_accounts (
    id         text primary key,
    name       text        not null unique,
    created_at timestamptz not null,
    expires_at timestamptz
);

insert into merchant_accounts (id, name, created_at) values ('acct_default', 'default', now());

create table principals (
    id         text primary key,
    name       text        not null,
    created_at timestamptz not null,
    account_id text        not null references merchant_accounts (id),
    constraint principals_account_name_key unique (account_id, name),
    constraint principals_id_account_key unique (id, account_id)
);

create table api_keys (
    id            text primary key,
    principal_id  text        not null,
    lookup_prefix text        not null,
    digest        bytea       not null,
    scopes        text        not null,
    environment   text        not null,
    created_at    timestamptz not null,
    expires_at    timestamptz,
    revoked_at    timestamptz,
    last_used_at  timestamptz,
    account_id    text        not null,
    constraint api_keys_principal_account_fkey foreign key (principal_id, account_id) references principals (id, account_id)
);

create index api_keys_lookup on api_keys (lookup_prefix);

create table admin_sessions (
    id         text primary key,
    expires_at timestamptz not null
);

create index admin_sessions_expires_at on admin_sessions (expires_at);

create table provider_credential_revisions (
    id         text        primary key,
    account_id text        not null references merchant_accounts (id),
    provider   text        not null,
    status     text        not null check (status in ('active', 'retired', 'revoked')),
    names      text        not null,
    key_id     text,
    nonce      bytea,
    ciphertext bytea,
    created_at timestamptz not null,
    retired_at timestamptz,
    revoked_at timestamptz,
    unique (id, account_id, provider),
    check ((key_id is null) = (nonce is null) and (nonce is null) = (ciphertext is null)),
    check (status <> 'active' or (key_id is not null and retired_at is null and revoked_at is null)),
    check (status <> 'retired' or (retired_at is not null and revoked_at is null)),
    check (status <> 'revoked' or (revoked_at is not null and key_id is null))
);

create unique index provider_credential_revisions_active on provider_credential_revisions (account_id, provider) where status = 'active';
create index provider_credential_revisions_retired on provider_credential_revisions (retired_at) where status = 'retired' and key_id is not null;

create table provider_credential_modes (
    account_id text        not null references merchant_accounts (id),
    provider   text        not null,
    mode       text        not null check (mode in ('platform', 'own')),
    changed_at timestamptz not null,
    primary key (account_id, provider)
);

create table payments (
    id                       text primary key,
    reference                text        not null,
    status                   text        not null,
    amount                   bigint      not null,
    currency                 text        not null,
    provider                 text        not null,
    provider_reference       text,
    provider_order_id        text        not null,
    payer_phone_number       text,
    description              text,
    return_url               text,
    next_action              text,
    metadata                 text,
    expires_at               timestamptz,
    completed_at             timestamptz,
    failure_reason           text,
    failure_detail           text,
    created_at               timestamptz not null,
    updated_at               timestamptz not null,
    callback_token_digest    bytea unique,
    creation_outcome_unknown boolean     not null default false,
    locale                   text,
    creation_principal       text,
    idempotency_key          text,
    request_fingerprint      text,
    version                  bigint      not null default 0,
    fee_amount               bigint check (fee_amount >= 0),
    fee_bearer               text check (fee_bearer in ('merchant', 'payer')),
    account_id               text        not null references merchant_accounts (id),
    reconciliation_attempts  integer     not null default 0,
    next_check_at            timestamptz,
    booked_at                timestamptz,
    credential_revision      text,
    constraint payments_account_reference_key unique (account_id, reference),
    constraint payments_credential_revision_fkey
        foreign key (credential_revision, account_id, provider) references provider_credential_revisions (id, account_id, provider),
    constraint payments_terminal_fields check (
        (status = 'pending' and completed_at is null)
        or (status <> 'pending' and completed_at is not null)
    ),
    constraint payments_failure_reason check ((status = 'failed') = (failure_reason is not null)),
    constraint payments_fee_complete check ((fee_amount is null) = (fee_bearer is null))
);

create unique index payments_provider_order on payments (provider, provider_order_id);
create index payments_pending_credential_revision on payments (credential_revision) where status = 'pending' and credential_revision is not null;
create index payments_reconciliation_turn on payments (coalesce(next_check_at, updated_at)) where status = 'pending';
create index payments_statement on payments (provider, booked_at, id) where status = 'succeeded';

create table payment_transitions (
    id          bigint generated always as identity primary key,
    payment_id  text        not null references payments (id),
    from_status text,
    to_status   text        not null,
    source      text        not null,
    recorded_at timestamptz not null
);

create index payment_transitions_payment on payment_transitions (payment_id, id);

create table payment_conflicts (
    id          bigint generated always as identity primary key,
    payment_id  text        not null references payments (id),
    source      text        not null,
    reported    text        not null,
    recorded_at timestamptz not null
);

create table indeterminate_operations (
    id          bigint generated always as identity primary key,
    payment_id  text        not null references payments (id),
    operation   text        not null,
    error       text        not null,
    recorded_at timestamptz not null
);

create table attestations (
    id             bigint generated always as identity primary key,
    payment_id     text        not null references payments (id),
    person         text        not null,
    status         text        not null check (status in ('succeeded', 'failed', 'expired', 'canceled')),
    failure_reason text,
    evidence       text        not null,
    recorded_at    timestamptz not null
);

create table idempotency_records (
    principal        text        not null,
    operation        text        not null,
    idempotency_key  text        not null,
    fingerprint      text        not null,
    response_status  integer,
    response_body    bytea,
    created_at       timestamptz not null,
    claimed_at       timestamptz not null,
    claim_generation bigint      not null default 1,
    primary key (principal, operation, idempotency_key)
);

create index idempotency_records_created_at on idempotency_records (created_at);

create table events (
    id              text primary key,
    payment_id      text        not null references payments (id),
    type            text        not null,
    sequence        integer     not null,
    previous_status text,
    created_at      timestamptz not null,
    body            bytea       not null,
    unique (payment_id, sequence)
);

create table webhook_endpoints (
    name       text primary key,
    url        text        not null,
    events     text        not null,
    enabled    boolean     not null default true,
    created_at timestamptz not null,
    account_id text        not null references merchant_accounts (id)
);

create table webhook_endpoint_state (
    endpoint              text primary key,
    consecutive_abandoned integer not null default 0,
    disabled_at           timestamptz,
    reason                text
);

create table webhook_deliveries (
    id              bigint generated always as identity primary key,
    event_id        text        not null references events (id),
    endpoint        text        not null,
    url             text        not null,
    status          text        not null check (status in ('pending', 'succeeded', 'abandoned', 'stopped')),
    attempts        integer     not null default 0,
    next_attempt_at timestamptz,
    updated_at      timestamptz not null,
    unique (event_id, endpoint)
);

create index webhook_deliveries_due on webhook_deliveries (next_attempt_at) where status = 'pending';

create table webhook_attempts (
    id          bigint generated always as identity primary key,
    delivery_id bigint      not null references webhook_deliveries (id),
    attempt     integer     not null,
    started_at  timestamptz not null,
    status_code integer,
    error       text,
    duration_ms integer     not null
);

create index webhook_attempts_delivery on webhook_attempts (delivery_id, attempt);

create table webhook_nonces (
    nonce      text primary key,
    expires_at timestamptz not null
);

create index webhook_nonces_expires_at on webhook_nonces (expires_at);

create table webhook_signing_keys (
    kid                    text primary key,
    public_key             bytea       not null,
    created_at             timestamptz not null,
    activates_at           timestamptz not null,
    retired_at             timestamptz,
    sealing_key_id         text        not null,
    private_key_nonce      bytea       not null,
    private_key_ciphertext bytea       not null
);

create table audit_records (
    id               bigint generated always as identity primary key,
    recorded_at      timestamptz not null,
    kind             text        not null,
    principal_id     text,
    lookup_prefix    text,
    operation        text        not null,
    request_id       text,
    result           text        not null,
    detail           text,
    remote_address   text,
    duration_ms      bigint,
    request_headers  jsonb,
    request_body     text,
    response_headers jsonb,
    response_body    text
);

create index audit_records_recorded on audit_records (recorded_at desc);
