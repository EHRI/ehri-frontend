INSERT INTO users (id, email, staff, active)
VALUES ('mike', 'user@example.com', true, true);

INSERT INTO import_dataset (repo_id, id, name, type, comments)
VALUES ('r1', 'default', 'Default', 'oaipmh', 'test');

INSERT INTO oaipmh_config
VALUES ('r1', 'default', 'http://example.com', 'ead', NULL, '2020-06-12 10:00:00', NULL);
