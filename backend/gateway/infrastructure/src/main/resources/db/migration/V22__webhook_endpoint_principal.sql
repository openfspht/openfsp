alter table webhook_endpoints add column principal_id text references principals (id);
