alter table payments
    add column creation_principal  text,
    add column idempotency_key     text,
    add column request_fingerprint text;
