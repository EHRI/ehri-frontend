ALTER TABLE import_dataset
    ADD COLUMN infer_hierarchy BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE import_config
    ADD COLUMN hierarchy_file VARCHAR(1024) NULL;
