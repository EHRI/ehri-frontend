# --- !Ups

/* NB: Syntax slightly mangled to be H2 compatible */

CREATE TABLE users (
    id          INT(10) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    profile_id  VARCHAR(255) NOT NULL
);

CREATE INDEX users_email ON users (email);
CREATE INDEX users_profile_id ON users (profile_id);

CREATE TABLE openid_association (
    id          INT(10) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     INT(10) NOT NULL,
    openid_url  VARCHAR(4096) NOT NULL
);

ALTER TABLE openid_association ADD CONSTRAINT openid_association_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE user_auth (
    id         INT(10) NOT NULL PRIMARY KEY,
    `data`     VARCHAR(255) NOT NULL
);

ALTER TABLE user_auth ADD CONSTRAINT user_auth_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

CREATE TABLE token (
  profile_id  VARCHAR(255) NOT NULL,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL
);

ALTER TABLE token ADD CONSTRAINT token_profile_id FOREIGN KEY (profile_id) REFERENCES users (profile_id) ON DELETE CASCADE;



# --- !Downs

DROP TABLE IF EXISTS token;
DROP TABLE IF EXISTS user_auth;
DROP TABLE IF EXISTS openid_association;
DROP TABLE IF EXISTS users;

