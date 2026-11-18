create table admin_sessions (
    id         text primary key,
    expires_at timestamptz not null
);

create index admin_sessions_expires_at on admin_sessions (expires_at);
