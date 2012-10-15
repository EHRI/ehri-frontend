# --- !Ups

CREATE TABLE users (
    profile_id  VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    UNIQUE(profile_id, email)
);

# --- !Downs

DROP TABLE IF EXISTS users;


