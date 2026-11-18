create table merchant_accounts (
    id         text primary key,
    name       text        not null unique,
    created_at timestamptz not null,
    expires_at timestamptz
);

insert into merchant_accounts (id, name, created_at) values ('acct_default', 'default', now());

alter table principals add column account_id text references merchant_accounts (id);
update principals set account_id = 'acct_default';
alter table principals alter column account_id set not null;
alter table principals drop constraint principals_name_key;
alter table principals add constraint principals_account_name_key unique (account_id, name);
alter table principals add constraint principals_id_account_key unique (id, account_id);

alter table api_keys add column account_id text;
update api_keys set account_id = 'acct_default';
alter table api_keys alter column account_id set not null;
alter table api_keys add constraint api_keys_principal_account_fkey foreign key (principal_id, account_id) references principals (id, account_id);

alter table payments add column account_id text references merchant_accounts (id);
update payments set account_id = 'acct_default';
alter table payments alter column account_id set not null;
alter table payments drop constraint payments_reference_key;
alter table payments add constraint payments_account_reference_key unique (account_id, reference);

delete from webhook_endpoints where principal_id is not null;
alter table webhook_endpoints add column account_id text references merchant_accounts (id);
update webhook_endpoints set account_id = 'acct_default';
alter table webhook_endpoints alter column account_id set not null;
alter table webhook_endpoints drop column principal_id;
