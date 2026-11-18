alter table payments add column booked_at timestamptz;
update payments set booked_at = completed_at where status = 'succeeded';
create index payments_statement on payments (provider, booked_at) where status = 'succeeded';
