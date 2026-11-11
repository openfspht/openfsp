create table attestation_keys (
    kid varchar(64) primary key,
    public_key varchar(64) not null,
    private_key bytea,
    created_at timestamp with time zone not null,
    retired_at timestamp with time zone
);

create table attestations (
    id uuid primary key,
    report_id uuid not null unique references reports (id) on delete cascade,
    issued_at timestamp with time zone not null,
    kid varchar(64) not null references attestation_keys (kid),
    jws text not null
);
