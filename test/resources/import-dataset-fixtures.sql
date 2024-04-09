INSERT INTO import_dataset (repo_id, id, name, type, item_id, nest, status, comments)
VALUES ('r1', 'default', 'Default', 'upload', NULL, false, 'active', 'test')
     , ('r2', 'default', 'Default', 'oaipmh', NULL, false, 'active', 'test')
     , ('r1', 'oaipmh_test', 'OaiPmh Test', 'oaipmh', NULL, false, 'active', 'test')
     , ('r1', 'rs_test', 'RS Test', 'rs', NULL, false, 'active', 'test')
     , ('r1', 'urlset_test', 'URL Set Test', 'urlset', NULL, false, 'active', 'test')
     , ('r1', 'nest_test', 'URL Set Test', 'urlset', 'nl-r1-m19', true, 'active', 'test')
     ;

