# --- !Ups

CREATE TABLE users (
    id          INT(10) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    profile_id  VARCHAR(255) NOT NULL
);

CREATE TABLE openid_association (
    id          INT(10) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     INT(10) NOT NULL,
    openid_url  VARCHAR(4096) NOT NULL,
    CONSTRAINT FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_auth (
    id         INT(10) NOT NULL PRIMARY KEY,
    `data`     VARCHAR(255) NOT NULL,
    CONSTRAINT FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);

# --- !Downs

DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS user_auth;
