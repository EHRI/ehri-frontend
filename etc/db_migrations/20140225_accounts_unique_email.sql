
/**
  Add unique constraint to users_email.
 */
ALTER TABLE users DROP INDEX users_email;
ALTER TABLE users ADD UNIQUE INDEX users_email (email);