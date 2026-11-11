create table attestations (
    id             bigint generated always as identity primary key,
    payment_id     text        not null references payments (id),
    person         text        not null,
    status         text        not null check (status in ('succeeded', 'failed', 'expired', 'canceled')),
    failure_reason text,
    evidence       text        not null,
    recorded_at    timestamptz not null
);
