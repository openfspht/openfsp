alter table idempotency_records add column claimed_at timestamptz;
update idempotency_records set claimed_at = created_at;
alter table idempotency_records alter column claimed_at set not null;
