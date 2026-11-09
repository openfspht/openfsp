create table indeterminate_operations (
    id          bigint generated always as identity primary key,
    payment_id  text        not null references payments (id),
    operation   text        not null,
    error       text        not null,
    recorded_at timestamptz not null
);
