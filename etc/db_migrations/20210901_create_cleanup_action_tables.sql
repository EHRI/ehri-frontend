CREATE TABLE cleanup_action(
   id                SERIAL PRIMARY KEY,
   repo_snapshot_id  INTEGER NOT NULL REFERENCES repo_snapshot(id) ON DELETE CASCADE,
   created           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cleanup_action_deletion(
   id                SERIAL PRIMARY KEY,
   cleanup_action_id INTEGER NOT NULL REFERENCES cleanup_action(id) ON DELETE CASCADE,
   item_id           TEXT NOT NULL
);

CREATE INDEX cleanup_action_deletion_item_id ON cleanup_action_deletion(item_id);

CREATE TABLE cleanup_action_redirect(
   id                SERIAL PRIMARY KEY,
   cleanup_action_id INTEGER NOT NULL REFERENCES cleanup_action(id) ON DELETE CASCADE,
   from_item_id      TEXT NOT NULL,
   to_item_id        TEXT NOT NULL
);

CREATE INDEX cleanup_action_redirect_from_item_id ON cleanup_action_redirect(from_item_id);
CREATE INDEX cleanup_action_redirect_to_item_id ON cleanup_action_redirect(to_item_id);

