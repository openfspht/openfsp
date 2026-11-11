create table webhook_endpoint_state (
    endpoint              text primary key,
    consecutive_abandoned integer not null default 0,
    disabled_at           timestamptz,
    reason                text
);
