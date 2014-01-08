# --- !Ups

CREATE TABLE users (
    id          VARCHAR(50) NOT NULL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    staff       BOOLEAN NOT NULL
);


CREATE TABLE openid_association (
    id           VARCHAR(50) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    openid_url   VARCHAR(255) NOT NULL,
    PRIMARY KEY(id, openid_url)
);

CREATE TABLE user_auth (
    id         VARCHAR(50) NOT NULL PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    data       VARCHAR(255) NOT NULL
);

CREATE TABLE token (
  id          VARCHAR(50) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL,
  is_sign_up  BOOLEAN NOT NULL DEFAULT FALSE
);


# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS user_auth;
