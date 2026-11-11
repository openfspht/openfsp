create table audit_records (
    id            bigint generated always as identity primary key,
    recorded_at   timestamptz not null,
    kind          text        not null,
    principal_id  text,
    lookup_prefix text,
    operation     text        not null,
    request_id    text,
    result        text        not null,
    detail        text
);

create index audit_records_recorded on audit_records (recorded_at desc);
