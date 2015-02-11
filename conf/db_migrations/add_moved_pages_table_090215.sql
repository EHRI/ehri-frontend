CREATE TABLE moved_pages(
  original_path_sha1 CHAR(40) PRIMARY KEY,
  original_path TEXT NOT NULL,
  new_path      TEXT NOT NULL,
  created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
