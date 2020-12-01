/*
  Add active flag on users
  */
START TRANSACTION;
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER staff;
UPDATE users SET active = 1;
COMMIT;
