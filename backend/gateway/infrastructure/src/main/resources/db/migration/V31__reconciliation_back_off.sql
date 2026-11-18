alter table payments add column reconciliation_attempts integer not null default 0;
alter table payments add column next_check_at timestamptz;
update payments set next_check_at = last_checked_at + interval '30 seconds' where last_checked_at is not null;
drop index payments_reconciliation_turn;
alter table payments drop column last_checked_at;
create index payments_reconciliation_turn on payments ((coalesce(next_check_at, updated_at))) where status = 'pending';
