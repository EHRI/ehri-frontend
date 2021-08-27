#--- !Ups

CREATE TABLE users (
    id              VARCHAR(50) NOT NULL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    staff           BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    allow_messaging BOOLEAN NOT NULL DEFAULT TRUE,
    created         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login      TIMESTAMP NULL,
    password        VARCHAR(255) NULL,
    is_legacy       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX users_email ON users (email);

CREATE TABLE openid_association (
    id         VARCHAR(50) NOT NULL,
    openid_url VARCHAR(255) NOT NULL,
    created    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, openid_url),
    CONSTRAINT openid_association_id
        FOREIGN KEY (id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE oauth2_association (
    id          VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    provider    VARCHAR(255) NOT NULL,
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, provider_id, provider),
    CONSTRAINT oauth2_association_id
        FOREIGN KEY (id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE token (
    id         VARCHAR(50) NOT NULL,
    token      VARCHAR(255) NOT NULL PRIMARY KEY,
    expires    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_sign_up BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT token_profile_id
        FOREIGN KEY (id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE moved_pages (
    original_path_sha1 CHAR(40) PRIMARY KEY,
    original_path      TEXT NOT NULL,
    new_path           TEXT NOT NULL,
    created            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE research_guide (
    id           SERIAL PRIMARY KEY,
    name         TEXT NOT NULL,
    path         VARCHAR(255) DEFAULT NULL,
    picture      VARCHAR(255) DEFAULT NULL,
    virtual_unit VARCHAR(255) DEFAULT NULL,
    description  TEXT,
    css          TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    default_page INTEGER NULL,
    CONSTRAINT research_guide_path_unique
        UNIQUE (path)
);

CREATE TABLE research_guide_page (
    id                SERIAL PRIMARY KEY,
    research_guide_id INTEGER NOT NULL,
    name              TEXT NOT NULL,
    layout            VARCHAR(45) DEFAULT NULL,
    content           TEXT,
    path              VARCHAR(45) DEFAULT NULL,
    position          VARCHAR(45) DEFAULT NULL,
    description       TEXT,
    params            VARCHAR(255) DEFAULT NULL,
    CONSTRAINT research_guide_page_id
        FOREIGN KEY (research_guide_id)
            REFERENCES research_guide (id)
            ON DELETE CASCADE,
    CONSTRAINT research_guide_path_guide_id
        UNIQUE (research_guide_id, path)
);

CREATE TABLE feedback (
    id      CHAR(10) NOT NULL PRIMARY KEY,
    user_id VARCHAR(50) DEFAULT NULL,
    name    VARCHAR(255) DEFAULT NULL,
    email   VARCHAR(255) DEFAULT NULL,
    text    TEXT,
    type    VARCHAR(10) DEFAULT NULL,
    copy    BOOLEAN DEFAULT FALSE,
    context TEXT,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP NULL,
    mode    VARCHAR(10) NOT NULL
);

CREATE TABLE cypher_queries (
    id          CHAR(10) NOT NULL PRIMARY KEY,
    user_id     VARCHAR(50) DEFAULT NULL,
    name        VARCHAR(255) NOT NULL,
    query       TEXT NOT NULL,
    description TEXT,
    public      BOOLEAN DEFAULT FALSE,
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated     TIMESTAMP NULL
);

CREATE TABLE import_dataset (
    repo_id         VARCHAR(50) NOT NULL,
    id              VARCHAR(50) NOT NULL,
    name            TEXT NOT NULL,
    type            VARCHAR(10) NOT NULL,
    content_type    VARCHAR(50) NULL,
    created         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    item_id         TEXT,
    sync            BOOLEAN DEFAULT FALSE,
    comments        TEXT,
    PRIMARY KEY (id, repo_id),
    UNIQUE (id, repo_id),
    CONSTRAINT import_dataset_id_pattern CHECK (id ~ '^[a-z0-9_]+$') ,
    CONSTRAINT import_dataset_item_id_pattern CHECK(item_id IS NULL OR item_id ~ concat('^', repo_id, '\-.+') )
);

CREATE TABLE oaipmh_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    endpoint_url      VARCHAR(512) NOT NULL,
    metadata_prefix   VARCHAR(255) NOT NULL,
    set_spec          VARCHAR(255),
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments          TEXT,
    PRIMARY KEY (repo_id, import_dataset_id),
    CONSTRAINT oaipmh_config_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);

CREATE TABLE resourcesync_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    endpoint_url      VARCHAR(512) NOT NULL,
    filter_spec       VARCHAR(512),
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments          TEXT,
    PRIMARY KEY (repo_id, import_dataset_id),
    CONSTRAINT resourcesync_config_repo_id_import_dataset_id
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
            ON DELETE CASCADE,
    CONSTRAINT harvest_event_user_id
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX harvest_event_repo_dataset ON harvest_event(repo_id, import_dataset_id);
CREATE INDEX harvest_event_repo_job ON harvest_event(repo_id, job_id);
CREATE INDEX harvest_event_user ON harvest_event(user_id);

CREATE TABLE data_transformation (
    id          CHAR(10) NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    repo_id     VARCHAR(50) NULL,
    type        VARCHAR(10) NOT NULL,
    map         TEXT NOT NULL,
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments    TEXT,
    has_params  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX data_transformation_name ON data_transformation (name);

CREATE TABLE transformation_config (
    repo_id                VARCHAR(50) NOT NULL,
    import_dataset_id      VARCHAR(50) NOT NULL,
    ordering               INTEGER NOT NULL,
    data_transformation_id CHAR(10) NOT NULL,
    parameters jsonb NOT NULL DEFAULT '{}'::jsonb,
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

CREATE TABLE import_config (
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    allow_updates     BOOLEAN NOT NULL DEFAULT FALSE,
    use_source_id     BOOLEAN NOT NULL DEFAULT FALSE,
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


CREATE TABLE import_log (
    id                SERIAL PRIMARY KEY,
    event_id          CHAR(36) NULL,
    repo_id           VARCHAR(50) NOT NULL,
    import_dataset_id VARCHAR(50) NOT NULL,
    created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT import_log_repo_id_import_dataset_id
        FOREIGN KEY (repo_id, import_dataset_id)
            REFERENCES import_dataset (repo_id, id)
            ON DELETE CASCADE
);

CREATE INDEX import_log_repo_dataset ON import_log (repo_id, import_dataset_id);
CREATE INDEX import_log_event_id ON import_log (event_id);

CREATE TABLE import_file_mapping (
    id            SERIAL PRIMARY KEY,
    import_log_id INT REFERENCES import_log (id) ON DELETE CASCADE,
    key           TEXT NOT NULL,
    version_id    VARCHAR(1024),
    item_id       TEXT NOT NULL,
    type          VARCHAR(10) NOT NULL
);

CREATE INDEX import_file_mapping_import_log_id ON import_file_mapping (import_log_id);
CREATE INDEX import_file_mapping_key ON import_file_mapping (key);
CREATE INDEX import_file_mapping_item_id ON import_file_mapping (item_id);
CREATE INDEX import_file_mapping_import_log_id_item_id ON import_file_mapping (import_log_id, item_id);

CREATE TABLE import_error(
  id                SERIAL PRIMARY KEY,
  import_log_id     INT REFERENCES import_log (id) ON DELETE CASCADE,
  key               TEXT NOT NULL,
  version_id        VARCHAR(1024),
  error_text        TEXT
);

CREATE INDEX import_error_key ON import_error (key);

CREATE TABLE repo_snapshot(
    id          SERIAL PRIMARY KEY,
    repo_id     VARCHAR(50) NOT NULL,
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes       TEXT NULL
);

CREATE INDEX repo_snapshot_repo_id ON repo_snapshot(repo_id);

CREATE TABLE repo_snapshot_item(
    id                  SERIAL PRIMARY KEY,
    repo_snapshot_id    INTEGER NOT NULL,
    item_id             TEXT NOT NULL,
    local_id            TEXT NOT NULL,
    CONSTRAINT repo_snapshot_item_repo_snapshot_id
        FOREIGN KEY (repo_snapshot_id)
            REFERENCES repo_snapshot (id)
            ON DELETE CASCADE
);

CREATE INDEX repo_snapshot_item_item_id ON repo_snapshot_item(item_id);
CREATE INDEX repo_snapshot_item_local_id ON repo_snapshot_item(local_id);


 # --- !Downs

DROP TABLE IF EXISTS repo_snapshot_item CASCADE;
DROP TABLE IF EXISTS repo_snapshot CASCADE;
DROP TABLE IF EXISTS import_error CASCADE;
DROP TABLE IF EXISTS import_file_mapping CASCADE;
DROP TABLE IF EXISTS import_log CASCADE;
DROP TABLE IF EXISTS import_config CASCADE;
DROP TABLE IF EXISTS data_transformation CASCADE;
DROP TABLE IF EXISTS transformation_config CASCADE;
DROP TABLE IF EXISTS harvest_event CASCADE;
DROP TABLE IF EXISTS resourcesync_config CASCADE;
DROP TABLE IF EXISTS oaipmh_config CASCADE;
DROP TABLE IF EXISTS import_dataset CASCADE;
DROP TABLE IF EXISTS cypher_queries CASCADE;
DROP TABLE IF EXISTS feedback CASCADE;
DROP TABLE IF EXISTS research_guide_page CASCADE;
DROP TABLE IF EXISTS research_guide CASCADE;
DROP TABLE IF EXISTS moved_pages CASCADE;
DROP TABLE IF EXISTS user_auth_token CASCADE;
DROP TABLE IF EXISTS token CASCADE;
DROP TABLE IF EXISTS openid_association CASCADE;
DROP TABLE IF EXISTS oauth2_association CASCADE;
DROP TABLE IF EXISTS users CASCADE;
