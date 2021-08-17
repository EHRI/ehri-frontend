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


