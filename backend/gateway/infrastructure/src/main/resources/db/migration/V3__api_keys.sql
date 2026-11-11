create table principals (
    id         text primary key,
    name       text        not null,
    created_at timestamptz not null
);

create table api_keys (
    id            text primary key,
    principal_id  text        not null references principals (id),
    lookup_prefix text        not null,
    digest        bytea       not null,
    scopes        text        not null,
    environment   text        not null,
    created_at    timestamptz not null,
    expires_at    timestamptz,
    revoked_at    timestamptz,
    last_used_at  timestamptz
);

create index api_keys_lookup on api_keys (lookup_prefix);
