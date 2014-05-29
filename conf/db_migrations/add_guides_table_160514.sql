/*
*	Add Guides MySQL Structure
*/

CREATE TABLE research_guide (
  id INTEGER(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) DEFAULT NULL,
  path VARCHAR(45) DEFAULT NULL,
  picture VARCHAR(255) DEFAULT NULL,
  description text,
  active BOOLEAN NULL DEFAULT 0,
  `default` int(1) DEFAULT 0
);

ALTER TABLE research_guide ADD CONSTRAINT research_guide_name_unique UNIQUE(name);
ALTER TABLE research_guide ADD CONSTRAINT research_guide_path_unique UNIQUE(path);

CREATE TABLE research_guide_page (
  id INTEGER(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  research_guide_id INTEGER(11) DEFAULT NULL,
  name VARCHAR(45) DEFAULT NULL,
  layout VARCHAR(45) DEFAULT NULL,
  cypher text,
  path VARCHAR(45) DEFAULT NULL,
  menu VARCHAR(45) DEFAULT NULL
);

ALTER TABLE research_guide_page ADD CONSTRAINT research_guide_page_id FOREIGN KEY (research_guide_id) REFERENCES research_guide (id) ON DELETE NO ACTION ON UPDATE CASCADE;