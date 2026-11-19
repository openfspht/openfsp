create table provider_credential_modes (
    account_id text        not null references merchant_accounts (id),
    provider   text        not null,
    mode       text        not null check (mode in ('platform', 'own')),
    changed_at timestamptz not null,
    primary key (account_id, provider)
);

insert into provider_credential_modes (account_id, provider, mode, changed_at)
select account_id, provider, 'own', now()
from provider_credential_revisions
group by account_id, provider;
