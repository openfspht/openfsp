alter table audit_records
    add column remote_address   text,
    add column duration_ms      bigint,
    add column request_headers  jsonb,
    add column request_body     text,
    add column response_headers jsonb,
    add column response_body    text;
