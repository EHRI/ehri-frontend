INSERT INTO users (id, email, staff, active)
VALUES ('mike', 'user@example.com', true, true);

INSERT INTO import_dataset (repo_id, id, name, type, comments)
VALUES ('r1', 'default', 'Default', 'oaipmh', 'test');

INSERT INTO import_config (repo_id, import_dataset_id, allow_updates, properties_file, default_lang, log_message)
VALUES ('r1', 'default', true, 'r1-ead.properties', 'eng', 'Testing');
