/*
  Add active flag on users
  */
START TRANSACTION;
ALTER TABLE users ADD COLUMN last_login DATETIME NULL AFTER created;
COMMIT;
