# --- !Ups

CREATE SEQUENCE openid_user_id_seq;

CREATE TABLE openid_user (
    id          INTEGER NOT NULL DEFAULT nextval('openid_user_id_seq') PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    profile_id  VARCHAR(255) NOT NULL
);

CREATE SEQUENCE openid_association_id_seq;

CREATE TABLE openid_association (
    id          INTEGER NOT NULL DEFAULT nextval('openid_association_id_seq') PRIMARY KEY,
    user_id     INTEGER NOT NULL,
    openid_url  TEXT NOT NULL
);

# --- !Downs

DROP TABLE IF EXISTS openid_user;
DROP SEQUENCE IF EXISTS openid_user_id_seq;
DROP TABLE IF EXISTS openid_association;
DROP SEQUENCE IF EXISTS openid_association_id_seq;

