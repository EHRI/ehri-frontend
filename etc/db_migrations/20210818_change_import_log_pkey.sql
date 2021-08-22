BEGIN TRANSACTION ;
ALTER TABLE import_log RENAME COLUMN id TO event_id;
ALTER TABLE import_log ALTER COLUMN event_id DROP NOT NULL;
ALTER TABLE import_log DROP CONSTRAINT import_log_pkey CASCADE;
ALTER TABLE import_log ADD COLUMN id SERIAL PRIMARY KEY;
ALTER TABLE import_file_mapping RENAME COLUMN import_log_id TO import_log_event_id;
ALTER TABLE import_file_mapping ADD COLUMN import_log_id INT;
CREATE INDEX import_log_event_id ON import_log (event_id);
UPDATE import_file_mapping SET import_log_id = (SELECT id FROM import_log WHERE event_id = import_log_event_id);
ALTER TABLE import_file_mapping ADD FOREIGN KEY (import_log_id) REFERENCES import_log;
ALTER TABLE import_file_mapping DROP COLUMN import_log_event_id;
COMMIT ;