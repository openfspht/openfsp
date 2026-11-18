alter table idempotency_records add column fingerprint_version integer not null default 1;
alter table idempotency_records alter column fingerprint_version drop default;
