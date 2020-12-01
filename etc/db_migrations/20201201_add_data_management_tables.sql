BEGIN TRANSACTION;

CREATE TABLE import_dataset (
    repo_id  VARCHAR(50) NOT NULL,
    id       VARCHAR(50) NOT NULL,
    name     TEXT NOT NULL,
    type     VARCHAR(10) NOT NULL,
    created  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments TEXT,
    PRIMARY KEY (id, repo_id),
    UNIQUE (id, repo_id),
    CONSTRAINT import_dataset_id_pattern CHECK (id ~ '^[a-z0-9_]+$')
);

CREATE TABLE oaipmh_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    endpoint_url      VARCHAR(512) NOT NULL,
    metadata_prefix   VARCHAR(10) NOT NULL,
    set_spec          VARCHAR(50),
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments          TEXT,
    PRIMARY KEY (repo_id, import_dataset_id),
    CONSTRAINT oaipmh_config_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);

CREATE TABLE harvest_event (
    id                SERIAL PRIMARY KEY,
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    job_id            VARCHAR(50) NOT NULL,
    user_id           VARCHAR(50) NULL,
    event_type        VARCHAR(50) NOT NULL,
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    info              TEXT,
    CONSTRAINT harvest_event_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE SET NULL,
    CONSTRAINT harvest_event_user_id
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE SET NULL
);

CREATE INDEX harvest_event_repo_job ON harvest_event (repo_id, job_id);

CREATE TABLE data_transformation (
    id       CHAR(10) NOT NULL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    repo_id  VARCHAR(50) NULL,
    type     VARCHAR(10) NOT NULL,
    map      TEXT NOT NULL,
    created  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments TEXT
);

CREATE UNIQUE INDEX data_transformation_name ON data_transformation (name);

CREATE TABLE transformation_config (
    repo_id                VARCHAR(50) NOT NULL,
    import_dataset_id      VARCHAR(50) NOT NULL,
    ordering               INTEGER NOT NULL,
    data_transformation_id CHAR(10) NOT NULL,
    PRIMARY KEY (repo_id, import_dataset_id, ordering),
    CONSTRAINT transformation_config_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE,
    CONSTRAINT transformation_config_dt_id
        FOREIGN KEY (data_transformation_id)
            REFERENCES data_transformation (id)
            ON DELETE CASCADE
);

CREATE TABLE import_log (
    id                CHAR(36) PRIMARY KEY,
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT import_log_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);

CREATE TABLE import_file_mapping (
    id            SERIAL PRIMARY KEY,
    import_log_id CHAR(36) REFERENCES import_log (id) ON DELETE CASCADE,
    key           TEXT NOT NULL,
    version_id    VARCHAR(1024),
    item_id       TEXT NOT NULL,
    type          VARCHAR(10) NOT NULL
);

COMMIT;
