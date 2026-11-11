create table webhook_endpoints (
    name       text primary key,
    url        text        not null,
    events     text        not null,
    enabled    boolean     not null default true,
    created_at timestamptz not null
);
