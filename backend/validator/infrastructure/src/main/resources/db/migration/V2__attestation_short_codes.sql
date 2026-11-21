ALTER TABLE attestations ADD COLUMN short_code VARCHAR(16);

UPDATE attestations SET short_code = SUBSTR(REPLACE(CAST(id AS TEXT), '-', ''), 1, 12);

ALTER TABLE attestations ALTER COLUMN short_code SET NOT NULL;

ALTER TABLE attestations ADD CONSTRAINT attestations_short_code_key UNIQUE (short_code);
