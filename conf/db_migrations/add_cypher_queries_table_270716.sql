CREATE TABLE cypher_queries (
  id CHAR(10) NOT NULL PRIMARY KEY ,
  user_id VARCHAR(50) DEFAULT NULL,
  name VARCHAR(255) NOT NULL,
  query TEXT NOT NULL,
  description TEXT,
  public BOOLEAN DEFAULT FALSE,
  created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated TIMESTAMP NULL
) CHARACTER SET utf8 COLLATE utf8_general_ci;