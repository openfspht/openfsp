create table provider_credential_revisions (
    id         text        primary key,
    account_id text        not null references merchant_accounts (id),
    provider   text        not null,
    status     text        not null check (status in ('active', 'retired', 'revoked')),
    names      text        not null,
    key_id     text,
    nonce      bytea,
    ciphertext bytea,
    created_at timestamptz not null,
    retired_at timestamptz,
    revoked_at timestamptz,
    unique (id, account_id, provider),
    check ((key_id is null) = (nonce is null) and (nonce is null) = (ciphertext is null)),
    check (status <> 'active' or (key_id is not null and retired_at is null and revoked_at is null)),
    check (status <> 'retired' or (retired_at is not null and revoked_at is null)),
    check (status <> 'revoked' or (revoked_at is not null and key_id is null))
);

create unique index provider_credential_revisions_active on provider_credential_revisions (account_id, provider) where status = 'active';
create index provider_credential_revisions_retired on provider_credential_revisions (retired_at) where status = 'retired' and key_id is not null;

insert into provider_credential_revisions (id, account_id, provider, status, names, key_id, nonce, ciphertext, created_at)
select 'cred_' || upper(replace(gen_random_uuid()::text, '-', '')), account_id, provider, 'active', names, key_id, nonce, ciphertext, updated_at
from provider_credentials;

insert into provider_credential_revisions (id, account_id, provider, status, names, created_at, retired_at)
select 'cred_' || upper(replace(gen_random_uuid()::text, '-', '')), orphan.account_id, orphan.provider, 'retired', '', now(), now()
from (
    select distinct p.account_id, p.provider from payments p
    where p.provider_credentials = 'account'
      and not exists (select 1 from provider_credentials c where c.account_id = p.account_id and c.provider = p.provider)
) orphan;

alter table payments add column credential_revision text;
update payments p set credential_revision = r.id
from provider_credential_revisions r
where p.provider_credentials = 'account' and r.account_id = p.account_id and r.provider = p.provider;
alter table payments add constraint payments_credential_revision_fkey
    foreign key (credential_revision, account_id, provider) references provider_credential_revisions (id, account_id, provider);
create index payments_pending_credential_revision on payments (credential_revision) where status = 'pending' and credential_revision is not null;
alter table payments drop column provider_credentials;

drop table provider_credentials;
