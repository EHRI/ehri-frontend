ALTER TABLE import_dataset
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'active',
    DROP CONSTRAINT import_dataset_item_id_pattern;
