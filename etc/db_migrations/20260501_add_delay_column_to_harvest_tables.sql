ALTER TABLE oaipmh_config
    ADD COLUMN delay INTEGER NULL;

ALTER TABLE oaipmh_config
    ADD CONSTRAINT oaipmh_config_delay_check CHECK ( delay > 0 );

ALTER TABLE resourcesync_config
    ADD COLUMN delay INTEGER NULL;

ALTER TABLE resourcesync_config
    ADD CONSTRAINT resourcesync_config_delay_check CHECK ( delay > 0 );

ALTER TABLE import_url_set_config
    ADD COLUMN delay INTEGER NULL;

ALTER TABLE import_url_set_config
    ADD CONSTRAINT import_url_set_config CHECK ( delay > 0 );

