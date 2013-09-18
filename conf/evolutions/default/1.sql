# --- !Ups

/* NB: Syntax slightly mangled to be H2 compatible */

CREATE TABLE users (
    id    VARCHAR(255) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL
);

CREATE INDEX users_email ON users (email);

CREATE TABLE openid_association (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    openid_url  VARCHAR(4096) NOT NULL
);

ALTER TABLE openid_association ADD CONSTRAINT openid_association_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE user_auth (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    `data`     VARCHAR(255) NOT NULL
);

ALTER TABLE user_auth ADD CONSTRAINT user_auth_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE token (
  id          VARCHAR(255) NOT NULL,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL
);

ALTER TABLE token ADD CONSTRAINT token_profile_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;



# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS user_auth;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS users;

