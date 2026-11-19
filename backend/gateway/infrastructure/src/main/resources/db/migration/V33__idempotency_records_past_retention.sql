delete from idempotency_records where created_at < now() - interval '7 days';
