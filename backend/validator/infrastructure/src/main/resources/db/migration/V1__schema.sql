CREATE TABLE reports (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    target VARCHAR(32) NOT NULL,
    base_url TEXT NOT NULL,
    core VARCHAR(32) NOT NULL,
    suite_version VARCHAR(32) NOT NULL,
    digest VARCHAR(64) NOT NULL,
    body TEXT NOT NULL
);

CREATE INDEX reports_created_at ON reports (created_at);

CREATE TABLE attestation_keys (
    kid VARCHAR(64) PRIMARY KEY,
    public_key VARCHAR(64) NOT NULL,
    private_key BYTEA,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retired_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE attestations (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    kid VARCHAR(64) NOT NULL REFERENCES attestation_keys (kid),
    jws TEXT NOT NULL
);
