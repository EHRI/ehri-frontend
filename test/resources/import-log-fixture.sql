INSERT INTO import_dataset (repo_id, id, name, type, comments) VALUES ('r1', 'default', 'Default', 'rs', 'Testing...');

INSERT INTO repo_snapshot (id, repo_id, created) VALUES (1, 'r1', '2021-09-01 10:00:00');

INSERT INTO repo_snapshot_item (repo_snapshot_id, item_id, local_id)
VALUES (1, 'c1', 'c1'),
       (1, 'c2', 'c2'),
       (1, 'c3', 'c3'),
       (1, 'c4', 'c4'),
       (1, 'nl-r1-m19', 'm19');

INSERT INTO import_log (id, event_id, repo_id, import_dataset_id, created)
VALUES (1, '1234-1234-1234-1234', 'r1', 'default', '2021-09-01 11:00:00');

INSERT INTO import_file_mapping (import_log_id, key, version_id, item_id, type)
VALUES (1, 'abc', 'v1', 'c1', 'updated'),
       (1, 'abc', 'v1', 'c2', 'updated'),
       (1, 'abc', 'v1', 'c3', 'updated'),
       (1, 'abc', 'v1', 'c1', 'updated');

INSERT INTO import_log (id, event_id, repo_id, import_dataset_id, created)
VALUES (2, '1234-1234-1234-1234', 'r1', 'default', '2021-09-01 11:01:00');

INSERT INTO import_file_mapping (import_log_id, key, version_id, item_id, type)
VALUES (2, 'abc', 'v1', 'nl-r1-TEST-m19', 'created');
