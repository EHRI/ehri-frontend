
/**
  Add a table for user login tokens
 */
CREATE TABLE user_auth_token(
  id          VARCHAR(50) NOT NULL,
  token       VARCHAR(255) NOT NULL PRIMARY KEY,
  expires     TIMESTAMP NOT NULL
);

ALTER TABLE user_auth_token ADD CONSTRAINT token_profile_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;
