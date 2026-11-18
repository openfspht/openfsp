alter table payments add column provider_credentials text not null default 'platform' check (provider_credentials in ('platform', 'account'));

create table provider_credentials (
    account_id text        not null references merchant_accounts (id),
    provider   text        not null,
    key_id     text        not null,
    nonce      bytea       not null,
    ciphertext bytea       not null,
    names      text        not null,
    updated_at timestamptz not null,
    primary key (account_id, provider)
);
