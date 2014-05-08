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



# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS user_auth;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS oauth2_association;
DROP TABLE IF EXISTS users;

