create table reports (
    id uuid primary key,
    created_at timestamp with time zone not null,
    target varchar(32) not null,
    base_url text not null,
    core varchar(32) not null,
    suite_version varchar(32) not null,
    digest char(64) not null,
    body text not null
);

create index reports_created_at on reports (created_at);
