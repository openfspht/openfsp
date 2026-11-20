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

create table attestation_keys (
    kid varchar(64) primary key,
    public_key varchar(64) not null,
    private_key bytea,
    created_at timestamp with time zone not null,
    retired_at timestamp with time zone
);

create table attestations (
    id uuid primary key,
    report_id uuid not null unique,
    issued_at timestamp with time zone not null,
    kid varchar(64) not null references attestation_keys (kid),
    jws text not null
);
