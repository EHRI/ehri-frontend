/*
*	Create guides tables
*/
CREATE TABLE `research_guide_page` (
  `id_research_guide_page` int(11) NOT NULL AUTO_INCREMENT,
  `id_research_guide` int(11) DEFAULT NULL,
  `name_research_guide_page` varchar(45) DEFAULT NULL,
  `layout_research_guide_page` varchar(45) DEFAULT NULL,
  `cypher_research_guide_page` text,
  `path_research_guide_page` varchar(45) DEFAULT NULL,
  `menu_research_guide_page` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id_research_guide_page`),
  KEY `fk_research_guide_page_1` (`id_research_guide`),
  KEY `index3` (`id_research_guide`),
  CONSTRAINT `fk_research_guide_page_1` FOREIGN KEY (`id_research_guide`) REFERENCES `research_guide` (`id_research_guide`) ON DELETE CASCADE ON UPDATE CASCADE
);


CREATE TABLE `research_guide` (
  `id_research_guide` int(11) NOT NULL AUTO_INCREMENT,
  `name_research_guide` varchar(255) DEFAULT NULL,
  `path_research_guide` varchar(45) DEFAULT NULL,
  `picture_research_guide` varchar(255) DEFAULT NULL,
  `description_research_guide` text,
  PRIMARY KEY (`id_research_guide`),
  UNIQUE KEY `name_research_guide_UNIQUE` (`name_research_guide`)
);