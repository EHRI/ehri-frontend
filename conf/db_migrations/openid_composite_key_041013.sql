/*
    Migrating docview db from old to new format: 04/10/2013

    MySQL
*/

ALTER TABLE openid_association DROP FOREIGN KEY openid_association_id;
ALTER TABLE user_auth DROP FOREIGN KEY user_auth_id;
ALTER TABLE token DROP FOREIGN KEY token_profile_id;

ALTER TABLE users MODIFY id VARCHAR(50) NOT NULL;
ALTER TABLE openid_association MODIFY id VARCHAR(50) NOT NULL;
ALTER TABLE openid_association MODIFY openid_url VARCHAR(255) NOT NULL;
ALTER TABLE openid_association DROP PRIMARY KEY;
ALTER TABLE openid_association ADD PRIMARY KEY (id, openid_url);
ALTER TABLE user_auth MODIFY id VARCHAR(50) NOT NULL;
ALTER TABLE token MODIFY id VARCHAR(50) NOT NULL;

ALTER TABLE openid_association ADD CONSTRAINT openid_association_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE user_auth ADD CONSTRAINT user_auth_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE token ADD CONSTRAINT token_profile_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;