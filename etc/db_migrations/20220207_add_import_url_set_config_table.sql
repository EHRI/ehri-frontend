CREATE TABLE import_url_set_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    url_map           JSONB NOT NULL,
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments          TEXT,
    PRIMARY KEY (repo_id, import_dataset_id),
    CONSTRAINT import_url_set_config_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);
