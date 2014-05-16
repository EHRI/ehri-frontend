# --- !Ups

/* NB: Syntax slightly mangled to be H2 compatible */

CREATE TABLE users (
    id    VARCHAR(50) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    staff BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    allow_messaging BOOLEAN NOT NULL DEFAULT TRUE,
    created TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX users_email ON users (email);

CREATE TABLE openid_association (
    id          VARCHAR(50) NOT NULL,
    openid_url  VARCHAR(255) NOT NULL,
    created     TIMESTAMP NOT NULL,
    PRIMARY KEY (id, openid_url)
);

ALTER TABLE openid_association ADD CONSTRAINT openid_association_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE oauth2_association (
  id            VARCHAR(50) NOT NULL,
  provider_id   VARCHAR(100) NOT NULL,
  provider      VARCHAR(10) NOT NULL,
  created       TIMESTAMP NOT NULL,
  PRIMARY KEY (id, provider_id, provider)
);

ALTER TABLE oauth2_association ADD CONSTRAINT oauth2_association_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE user_auth (
    id          VARCHAR(50) NOT NULL PRIMARY KEY,
    `data`     VARCHAR(255) NOT NULL
);

ALTER TABLE user_auth ADD CONSTRAINT user_auth_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE token (
  id          VARCHAR(50) NOT NULL,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL,
  is_sign_up  BOOLEAN NOT NULL DEFAULT 0
);

ALTER TABLE token ADD CONSTRAINT token_profile_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

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

# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS user_auth;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS oauth2_association;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS research_guide_page;
DROP TABLE IF EXISTS research_guide;