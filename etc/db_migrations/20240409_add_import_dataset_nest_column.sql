ALTER TABLE import_dataset
    ADD COLUMN nest BOOLEAN NOT NULL DEFAULT FALSE,
    DROP CONSTRAINT import_dataset_item_id_pattern;
