/*
    Migrating docview db from old to new format: 20/09/2013

    MySQL
*/

/* NB: Syntax slightly mangled to be H2 compatible */

CREATE TABLE users_tmp (
    id    VARCHAR(255) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    staff BOOLEAN NOT NULL,
    created TIMESTAMP NOT NULL
);


CREATE TABLE openid_association_tmp (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    openid_url  VARCHAR(4096) NOT NULL,
    created     TIMESTAMP NOT NULL
);    
    

ALTER TABLE openid_association_tmp ADD CONSTRAINT openid_association_id FOREIGN KEY (id) REFERENCES users_tmp (id) ON DELETE CASCADE;

CREATE TABLE user_auth_tmp (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    `data`     VARCHAR(255) NOT NULL
);

ALTER TABLE user_auth_tmp ADD CONSTRAINT user_auth_id FOREIGN KEY (id) REFERENCES users_tmp (id) ON DELETE CASCADE;

CREATE TABLE token_tmp (
  id          VARCHAR(255) NOT NULL,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL
);

ALTER TABLE token_tmp ADD CONSTRAINT token_profile_id FOREIGN KEY (id) REFERENCES users_tmp (id) ON DELETE CASCADE;


INSERT INTO users_tmp (id,email,staff)
    SELECT profile_id, email, true FROM users;

INSERT INTO openid_association_tmp (id, openid_url)
    SELECT profile_id, openid_url FROM users, openid_association WHERE users.id = openid_association.user_id;

INSERT INTO user_auth_tmp (id, `data`)
    SELECT profile_id, `data` FROM users, user_auth WHERE users.id = user_auth.id;

/* Don't need to migrate tokens... */

DROP INDEX users_email ON users;
DROP INDEX users_profile_id ON users;

DROP TABLE users;
DROP TABLE openid_association;
DROP TABLE user_auth;
DROP TABLE token;
DROP TABLE play_evolutions; /* Not using these any more */


ALTER TABLE users_tmp RENAME TO users;
ALTER TABLE openid_association_tmp RENAME TO openid_association;
ALTER TABLE user_auth_tmp RENAME TO user_auth;
ALTER TABLE token_tmp RENAME TO token;

CREATE INDEX users_email ON users (email);
CREATE INDEX openid_association_openid_url ON openid_association (openid_url);


