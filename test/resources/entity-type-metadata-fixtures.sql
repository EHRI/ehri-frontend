INSERT INTO entity_type_meta(entity_type, name, description)
VALUES
        ('DocumentaryUnitDescription', 'Documentary Unit Description', 'A description of a documentary unit.'),
        ('RepositoryDescription', 'Repository Description', 'A description of a repository.');

INSERT INTO field_meta (entity_type, id, name, usage, description, see_also)
VALUES
        ('DocumentaryUnitDescription', 'locationOfOriginals', 'Location of Originals', 'desirable', 'The location of the original materials.', ARRAY['https://eadiva.com/originalsloc/', 'https://www.loc.gov/ead/tglib/elements/originalsloc.html']),
        ('RepositoryDescription', 'history', 'History', null, 'A history of the repository.', ARRAY[]::text[]);
