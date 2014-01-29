
/*
    Create Oauth table.
 */

START TRANSACTION;
CREATE TABLE oauth2_association (
  id            VARCHAR(50) NOT NULL,
  provider_id   VARCHAR(100) NOT NULL,
  provider      VARCHAR(10) NOT NULL,
  created       TIMESTAMP NOT NULL,
  PRIMARY KEY (id, provider_id, provider)
);

ALTER TABLE oauth2_association ADD CONSTRAINT oauth2_association_id FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE;

/*
  Add verified flag on users and is_sign_up on token
  */

ALTER TABLE users ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE AFTER email;
UPDATE users SET verified = 1;
ALTER TABLE token ADD COLUMN is_sign_up BOOLEAN NOT NULL DEFAULT FALSE AFTER expires;
COMMIT;
