drop table statement_entries;

drop table statements;

create index payments_succeeded_by_provider on payments (provider, completed_at, id) where status = 'succeeded';
