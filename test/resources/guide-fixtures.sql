INSERT INTO research_guide (
 id,
name,
path,
picture,
virtual_unit,
description,
`css`,
active,
`default`)
VALUES
 (1,'Terezín Research Guide','terezin','http://localhost:9000/assets/img/Terezin.jpg','michal-frankl-ehri-terezin-research-guide','frrrrr.','.guide-title {\r\nbackground-color:black;\r\n}',1,3)
 ,(2,'Jewish community','jewishcommunity','http://www.turismodigital.com/lala.jpg','michal-frankl-ehri-vienna-research-guide','Michal Frankl Ehri',NULL,1,9);

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
 (2,1,'People','person','terezin-victims','people','top',NULL,'')
 ,(3,1,'Places','map','terezin-places','places','top',NULL,'lat=50.51041091209279&lng=14.149961471557617')
 ,(4,1,'Organisations','organisation','terezin-jewishcouncil','organisations','top',NULL,NULL)
 ,(5,1,'Keywords','organisation','terezin-terms','keywords','top',NULL,NULL)
 ,(6,1,'About the guide','md','<div id=\"red\"><p>Paragraphe 1</p><p>Paragraphe 2</p></div>','about','side','$(\"#red\").css(\"background-color\", \"red\");',NULL)
 ,(7,2,'Organisations','organisation','wien-organisations','organisations','top',NULL,NULL)
 ,(8,2,'Keywords','organisation','wien-terms','keywords','top',NULL,NULL)
 ,(9,2,'Places','map','wien-places','places','top',NULL,'lat=48.21632348686661&lng=16.374521255493164')
 ,(10,2,'Persons','person','wien-persons','persons','top',NULL,NULL)
 ,(11,2,'Victims','person','wien-victims','victims','side',NULL,NULL)
 ,(13,1,'History of the Terezín ghetto','md','History of the Terezín ghetto\r\n------------------------------------\r\n![Image](http://www.ehri-project.eu/sites/default/files/imagecache/Photo/Wendy%20Lower3.jpg)','history','side',NULL,NULL)
 ,(14,1,'Databases of Terezín prisoners','md','# Databases of Terezín prisoners','prisoner_databases','side',NULL,NULL);
