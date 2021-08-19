CREATE TABLE coreference(
    id      SERIAL PRIMARY KEY,
    repo_id VARCHAR(50) NOT NULL UNIQUE,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP
);

CREATE INDEX coreference_repo_id ON coreference(repo_id);

CREATE TABLE coreference_value(
    id              SERIAL PRIMARY KEY,
    coreference_id  INT REFERENCES coreference(id) ON DELETE CASCADE,
    text            TEXT NOT NULL,
    target_id       TEXT NOT NULL,
    set_id          TEXT NOT NULL,
    UNIQUE(coreference_id, text, target_id, set_id)
);

CREATE INDEX coreference_value_text ON coreference_value(text);
CREATE INDEX coreference_value_target_id ON coreference_value(target_id);
CREATE INDEX coreference_value_set_id ON coreference_value(set_id);

