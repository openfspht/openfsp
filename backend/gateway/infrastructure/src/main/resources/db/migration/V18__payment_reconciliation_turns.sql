alter table payments add column last_checked_at timestamptz;

create index payments_reconciliation_turn on payments ((coalesce(last_checked_at, updated_at))) where status = 'pending';
