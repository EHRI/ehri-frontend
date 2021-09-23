INSERT INTO import_dataset (repo_id, id, name, type, status, comments)
VALUES ('r1', 'default', 'Default', 'upload', 'active', 'test'),
       ('r2', 'default', 'Default', 'oaipmh', 'active', 'test');

INSERT INTO data_transformation (id, name, repo_id, type, map, comments)
VALUES ('FG8jdRd43j', 'test', 'r1', 'xquery', 'test', 'test');
