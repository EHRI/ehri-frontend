/*
  Add active flag on users
  */
START TRANSACTION;
ALTER TABLE users
  ADD COLUMN password VARCHAR(255) NULL AFTER last_login,
  ADD COLUMN is_legacy BOOLEAN NOT NULL DEFAULT false AFTER password;
UPDATE users
    LEFT JOIN user_auth ON users.id = user_auth.id
  SET users.password = user_auth.data;
DROP TABLE user_auth;
COMMIT;
