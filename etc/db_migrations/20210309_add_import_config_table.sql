CREATE TABLE import_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    allow_updates     BOOLEAN NOT NULL DEFAULT FALSE,
    tolerant          BOOLEAN NOT NULL DEFAULT FALSE,
    properties_file   VARCHAR(1024) NULL,
    default_lang      CHAR(3) NULL,
    log_message       TEXT,
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments          TEXT NULL,
    PRIMARY KEY (repo_id, import_dataset_id),
    CONSTRAINT import_config_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);

