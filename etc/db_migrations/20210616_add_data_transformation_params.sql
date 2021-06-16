ALTER TABLE data_transformation ADD COLUMN has_params BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transformation_config ADD COLUMN parameters jsonb NOT NULL DEFAULT '{}'::jsonb;
