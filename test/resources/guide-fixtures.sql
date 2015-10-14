DELETE FROM research_guide WHERE id IN (1001, 1002);
DELETE FROM research_guide_page WHERE id IN (1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1013);

INSERT INTO research_guide (
 id,
name,
path,
picture,
virtual_unit,
description,
css,
active,
default_page)
VALUES
 (1001,'Terezín Research Guide','terezin','http://localhost:9000/assets/img/Terezin.jpg','michal-frankl-ehri-terezin-research-guide','frrrrr.','.guide-title {\r\nbackground-color:black;\r\n}',TRUE,NULL)
 ,(1002,'Jewish community','jewishcommunity','http://www.turismodigital.com/lala.jpg','michal-frankl-ehri-vienna-research-guide','Michal Frankl Ehri',NULL,TRUE,NULL);

INSERT INTO research_guide_page (
id,
research_guide_id,
name,
layout,
content,
path,
position,
description,
params)
VALUES
 (1001,1001,'People','person','terezin-victims','people','top',NULL,'')
 ,(1002,1001,'Places','map','terezin-places','places','top',NULL,'lat=50.51041091209279&lng=14.149961471557617')
 ,(1003,1001,'Organisations','organisation','terezin-jewishcouncil','organisations','top',NULL,NULL)
 ,(1004,1001,'Keywords','organisation','terezin-terms','keywords','top',NULL,NULL)
 ,(1005,1001,'About the guide','html','<div id=\"red\"><p>Paragraphe 1</p><p>Paragraphe 2</p></div>','about','side','$(\"#red\").css(\"background-color\", \"red\");',NULL)
 ,(1006,1002,'Organisations','organisation','wien-organisations','organisations','top',NULL,NULL)
 ,(1007,1002,'Keywords','organisation','wien-terms','keywords','top',NULL,NULL)
 ,(1008,1002,'Places','map','wien-places','places','top',NULL,'lat=48.21632348686661&lng=16.374521255493164')
 ,(1010,1002,'Persons','person','wien-persons','persons','top',NULL,NULL)
 ,(1011,1002,'Victims','person','wien-victims','victims','side',NULL,NULL)
 ,(1012,1001,'History of the Terezín ghetto','md','History of the Terezín ghetto\r\n------------------------------------\r\n![Image](http://www.ehri-project.eu/sites/default/files/imagecache/Photo/Wendy%20Lower3.jpg)','history','side',NULL,NULL)
 ,(1013,1001,'Databases of Terezín prisoners','md','# Databases of Terezín prisoners','prisoner_databases','side',NULL,NULL);
