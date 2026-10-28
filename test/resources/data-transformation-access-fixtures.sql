INSERT INTO import_dataset (repo_id, id, name, type, status, comments)
VALUES ('r1', 'default', 'Default', 'upload', 'active', 'test'),
       ('r2', 'default', 'Default', 'upload', 'active', 'test');

INSERT INTO data_transformation (id, name, repo_id, type, map, comments)
VALUES ('r1private1', 'r1 private', 'r1', 'xquery', 'test', 'test'),
       ('r2private1', 'r2 private', 'r2', 'xquery', 'test', 'test'),
       ('generic001', 'generic', NULL, 'xquery', 'test', 'test');
