create table webhook_signing_keys (
    kid         text primary key,
    private_key bytea       not null,
    public_key  bytea       not null,
    status      text        not null check (status in ('active', 'retired')),
    created_at  timestamptz not null
);

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
    id           bigint generated always as identity primary key,
    delivery_id  bigint      not null references webhook_deliveries (id),
    attempt      integer     not null,
    started_at   timestamptz not null,
    status_code  integer,
    error        text,
    duration_ms  integer     not null
);

create index webhook_attempts_delivery on webhook_attempts (delivery_id, attempt);
