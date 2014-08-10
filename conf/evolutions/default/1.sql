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

CREATE TABLE research_guide (
  id INTEGER(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) DEFAULT NULL,
  path VARCHAR(45) DEFAULT NULL,
  picture VARCHAR(255) DEFAULT NULL,
  virtual_unit VARCHAR(255) DEFAULT NULL,
  description text,
  active INT(1) NULL DEFAULT 0,
  `default` int(1) DEFAULT 0
);

ALTER TABLE research_guide ADD CONSTRAINT research_guide_name_unique UNIQUE(name);
ALTER TABLE research_guide ADD CONSTRAINT research_guide_path_unique UNIQUE(path);

CREATE TABLE research_guide_page (
  id INTEGER(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  research_guide_id INTEGER(11) DEFAULT NULL,
  name VARCHAR(45) DEFAULT NULL,
  layout VARCHAR(45) DEFAULT NULL,
  content text,
  path VARCHAR(45) DEFAULT NULL,
  position VARCHAR(45) DEFAULT NULL,
  params VARCHAR(255) DEFAULT NULL
);

ALTER TABLE research_guide_page ADD CONSTRAINT research_guide_page_id FOREIGN KEY (research_guide_id) REFERENCES research_guide (id) ON DELETE CASCADE;

# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS user_auth;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS oauth2_association;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS research_guide_page;
DROP TABLE IF EXISTS research_guide;
