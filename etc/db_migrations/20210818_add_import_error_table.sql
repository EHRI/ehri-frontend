CREATE TABLE import_error(
    id                SERIAL PRIMARY KEY,
    import_log_id     INT REFERENCES import_log (id) ON DELETE CASCADE,
    key               TEXT NOT NULL,
    version_id        VARCHAR(1024),
    error_text        TEXT
);

CREATE INDEX import_error_key ON import_error (key);

