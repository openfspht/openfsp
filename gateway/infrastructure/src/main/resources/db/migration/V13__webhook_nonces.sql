create table webhook_nonces (
    nonce      text primary key,
    expires_at timestamptz not null
);

create index webhook_nonces_expires_at on webhook_nonces (expires_at);
