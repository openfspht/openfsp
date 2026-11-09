alter table payments add column callback_token_digest bytea unique;

create table statements (
    id            text primary key,
    provider      text        not null,
    business_date date        not null,
    complete      boolean     not null default false,
    fetched_at    timestamptz not null,
    unique (provider, business_date)
);

create table statement_entries (
    id                 bigint generated always as identity primary key,
    statement_id       text        not null references statements (id),
    provider_reference text        not null,
    type               text        not null check (type in ('payment', 'reversal', 'fee', 'adjustment')),
    amount             bigint      not null,
    fee_amount         bigint,
    net_amount         bigint,
    booked_at          timestamptz not null,
    provider_order_id  text,
    payment_id         text references payments (id),
    match              text        not null check (match in ('matched', 'unmatched', 'conflict')),
    source             text        not null check (source in ('provider', 'import')),
    unique (statement_id, provider_reference)
);

create index statement_entries_match on statement_entries (match, booked_at, id);
