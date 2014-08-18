INSERT INTO research_guide (
  id,
  name,
  path,
  picture,
  virtual_unit,
  description,
  active,
  `default`
) VALUES (
  1, 'Terezín Research Guide','terezin','http://localhost:9000/assets/images/Terezin.jpg','michal-frankl-ehri-terezin-research-guide','The aim of the EHRI Terezín Research Guide is to create a comprehensive, innovative and easy to use guide through the dispersed and fragmented Terezín (Theresienstadt) archival material and to empower further research on the history of the ghetto. The Terezín Research Guide illustrates the primary raison d''être of EHRI - to connect collections spread in many archives and in more countries. EHRI research guides demonstrate what a collaborative archival project can achieve and how archivists can redefine their tasks beyond providing physical access and creating finding aids restricted to the local collections. The guide does not aim to make the existing archives irrelevant by placing all information online, but to help researchers identify relevant sources and to connect and compare them to documents in other collections. The guide will function as a gateway to the Terezín archival resources and - as an increasing amount of digitised material appears online - it will point to the respective public online catalogues.',1,3),
(2,'Jewish community','jewishcommunity','http://www.turismodigital.com/fotos/llegar-moverse-alojarse-y-ver-praga-de-mochilero-2-445.jpg','michal-frankl-ehri-vienna-research-guide','Michal Frankl Ehri',1,9);

INSERT INTO research_guide_page (
 id,
 research_guide_id,
 name,
 layout,
 content,
 path,
 position,
 params
) VALUES (2,1,'People','person','terezin-victims','people','top',''),(3,1,'Places','map','terezin-places','places','top','lat=50.51041091209279&lng=14.149961471557617'),(4,1,'Organisations','organisation','terezin-jewishcouncil','organisations','top',NULL),(5,1,'Keywords','organisation','terezin-terms','keywords','top',NULL),(6,1,'About the guide','md','# Why a Terezín Research Guide?\r\n\r\n# What is included?\r\n\r\n## Vestras cava ipsum dempto hac indiciumque iniectam\r\n\r\nLorem markdownum Aglauros videam Saturnia, est locutus video Sedit temptemus\r\nscilicet, penetrabile iunxit et numen. Honor adicit carchesia avus fecisses\r\ndextram contingat utrique corpore; tamen porrigit iacentes pulsabantque. Cera\r\nsociati manus. Imagine sed, suos metuit plangore aures cognitus ferrugine\r\n**magis**, non negant proles, moenia: pro, datis?\r\n\r\n> Quam res, despicit; diu\r\n> [dumque](http://en.wikipedia.org/wiki/Sterling_Archer), relinquentur invia,\r\n> veri. Domum illa atque **dolore quae** inquit loca, matura, sed solum! Excipit\r\n> raucaque diras vel: titulus si salus fratres nisi. Dum summa insonuit,\r\n> ulterius proles bifidosque multa, addidit visum: est diffudit, praesagaque\r\n> linigera.\r\n\r\n## Patitur in det\r\n\r\nNymphe noctis et sumimus dextra famulis; hoc vidit, et sed placeat animas\r\nsollemni. Praesagia tempore illo, una signa munimine; et **bacae et** ore nives,\r\ngaudetque superi: maiorque, et. Vidit ei ad [emensas poteratque\r\nmotura](http://textfromdog.tumblr.com/) cura Cereris potest\r\n[victor](http://en.wikipedia.org/wiki/Sterling_Archer); Ganymedis recentes\r\nlevati Athamantis. Amor et reddat gerunt postquam da pastoris sententia superba\r\nexcussae et latent tempore, et.\r\n\r\n## Cognitus et vidi collecta\r\n\r\nVias levitate adlevat convivia quo vincis et si, nocturnae rura suo plebe\r\nsinamus amorem laetos. Tum igitur excussa viri: videri morte nova pede iaculum\r\n**tenues ut**. Terra super in nisi, ut movebant lacertis.','about','side',NULL),(7,2,'Organisations','organisation','wien-organisations','organisations','top',NULL),(8,2,'Keywords','organisation','wien-terms','keywords','top',NULL),(9,2,'Places','map','wien-places','places','top','lat=48.21632348686661&lng=16.374521255493164'),(10,2,'Persons','person','wien-persons','persons','top',NULL),(11,2,'Victims','person','wien-victims','victims','side',NULL),(13,1,'History of the Terezín ghetto','md','History of the Terezín ghetto\r\n------------------------------------\r\n![Image](http://www.ehri-project.eu/sites/default/files/imagecache/Photo/Wendy%20Lower3.jpg)','history','side',NULL),(14,1,'Databases of Terezín prisoners','md','# Databases of Terezín prisoners','prisoner_databases','side',NULL);
