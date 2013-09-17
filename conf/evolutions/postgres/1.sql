# --- !Ups

CREATE SEQUENCE users_id_seq;

CREATE TABLE users (
    id          INTEGER NOT NULL DEFAULT nextval('users_id_seq') PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    profile_id  VARCHAR(255) NOT NULL
);

CREATE SEQUENCE openid_association_id_seq;

CREATE TABLE openid_association (
    id          INTEGER NOT NULL DEFAULT nextval('openid_association_id_seq') PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    openid_url  TEXT NOT NULL
);

CREATE TABLE user_auth (
    id         INTEGER NOT NULL PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    data       VARCHAR(255) NOT NULL
);

CREATE TABLE token (
  profile_id  VARCHAR(255) NOT NULL REFERENCES users (profile_id) ON DELETE CASCADE,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL
);


# --- !Downs

DROP TABLE IF EXISTS users;
DROP SEQUENCE IF EXISTS users_id_seq;
DROP TABLE IF EXISTS openid_association;
DROP SEQUENCE IF EXISTS openid_association_id_seq;
DROP TABLE IF EXISTS user_auth;
