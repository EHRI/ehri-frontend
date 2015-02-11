-- Increase path varchar size
ALTER TABLE research_guide MODIFY COLUMN path VARCHAR(255);
ALTER TABLE research_guide_page MODIFY COLUMN path VARCHAR(255);

-- Create a new column and copy contents of name column
ALTER TABLE research_guide ADD COLUMN name_tmp TEXT NOT NULL AFTER id;
UPDATE research_guide SET name_tmp = name;
ALTER TABLE research_guide DROP COLUMN name;
ALTER TABLE research_guide CHANGE name_tmp name TEXT NOT NULL;       

-- Create a new column and copy contents of name column
ALTER TABLE research_guide_page ADD COLUMN name_tmp TEXT NOT NULL AFTER id;
UPDATE research_guide_page SET name_tmp = name;
ALTER TABLE research_guide_page DROP COLUMN name;
ALTER TABLE research_guide_page CHANGE name_tmp name TEXT NOT NULL;

