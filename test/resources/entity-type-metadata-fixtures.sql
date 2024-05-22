INSERT INTO entity_type_meta(entity_type, name, description)
VALUES
        ('DocumentaryUnit', 'Documentary Unit Description', 'A description of a documentary unit.'),
        ('Repository', 'Repository Description', 'A description of a repository.');

INSERT INTO field_meta (entity_type, id, name, usage, description, category, see_also)
VALUES
        ('DocumentaryUnit', 'locationOfOriginals', 'Location of Originals', 'desirable', 'The location of the original materials.', 'materialsArea', ARRAY['https://eadiva.com/originalsloc/', 'https://www.loc.gov/ead/tglib/elements/originalsloc.html']),
        ('Repository', 'history', 'History', null, 'A history of the repository.', 'contextArea', ARRAY[]::text[]);
