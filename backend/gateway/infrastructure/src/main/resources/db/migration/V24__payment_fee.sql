alter table payments
    add column fee_amount bigint check (fee_amount >= 0),
    add column fee_bearer text check (fee_bearer in ('merchant', 'payer')),
    add constraint payments_fee_complete check ((fee_amount is null) = (fee_bearer is null));
