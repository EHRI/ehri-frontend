INSERT INTO coreference (id, repo_id, created, updated)
VALUES (9999, 'r1', '2021-08-19 10:00:00', '2021-08-19 10:00:00');

INSERT INTO coreference_value(id, coreference_id, text, target_id, set_id)
VALUES (9998, 9999, 'Person 1', 'a1', 'auths'),
       (9999, 9999, 'Person 2', 'a2', 'auths');
