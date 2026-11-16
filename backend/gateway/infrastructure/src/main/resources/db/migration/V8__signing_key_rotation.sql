alter table webhook_signing_keys add column activates_at timestamptz;
alter table webhook_signing_keys add column retired_at timestamptz;
update webhook_signing_keys set activates_at = created_at;
alter table webhook_signing_keys alter column activates_at set not null;
alter table webhook_signing_keys drop column status;
