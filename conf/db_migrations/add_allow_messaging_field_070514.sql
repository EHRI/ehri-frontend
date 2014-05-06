/*
  Add active flag on users
  */
START TRANSACTION;
ALTER TABLE users ADD COLUMN allow_messaging BOOLEAN NOT NULL DEFAULT TRUE AFTER active;
UPDATE users SET allow_messaging = 1;
COMMIT;
