create table payments (
    id                 text primary key,
    reference          text        not null unique,
    status             text        not null,
    amount             bigint      not null,
    currency           text        not null,
    provider           text        not null,
    provider_reference text,
    provider_order_id  text        not null,
    payer_phone_number text,
    description        text,
    return_url         text,
    next_action        text,
    metadata           text,
    expires_at         timestamptz,
    completed_at       timestamptz,
    failure_reason     text,
    failure_detail     text,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    constraint payments_terminal_fields check (
        (status = 'pending' and completed_at is null)
        or (status <> 'pending' and completed_at is not null)
    ),
    constraint payments_failure_reason check ((status = 'failed') = (failure_reason is not null))
);

create unique index payments_provider_order on payments (provider, provider_order_id);

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

create table idempotency_records (
    principal       text        not null,
    operation       text        not null,
    idempotency_key text        not null,
    fingerprint     text        not null,
    response_status integer,
    response_body   bytea,
    created_at      timestamptz not null,
    primary key (principal, operation, idempotency_key)
);
