INSERT INTO field_meta (entity_type, id, name, usage, description, see_also)
VALUES
        ('DocumentaryUnitDescription', 'locationOfOriginals', 'Location of Originals', 'desirable', 'The location of the original materials.', ARRAY['https://eadiva.com/originalsloc/']),
        ('RepositoryDescription', 'history', 'History', null, 'A history of the repository.', ARRAY[]::text[]);
