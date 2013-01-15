# --- !Ups

CREATE TABLE user_auth (
    id         INTEGER NOT NULL PRIMARY KEY,
    data       VARCHAR(255) NOT NULL
);

# --- !Downs

DROP TABLE IF EXISTS user_auth;

