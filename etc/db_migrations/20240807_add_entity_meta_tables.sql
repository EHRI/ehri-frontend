BEGIN TRANSACTION;

CREATE TABLE entity_type_meta(
    entity_type VARCHAR(50) NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated     TIMESTAMP
);

CREATE INDEX entity_type_meta_entity_type ON entity_type_meta(entity_type);

CREATE TABLE field_meta(
    entity_type VARCHAR(50) NOT NULL,
    id          VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    usage       VARCHAR(50),
    category    VARCHAR(50),
    default_val TEXT,
    see_also    TEXT[],
    created     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated     TIMESTAMP,
    PRIMARY KEY (entity_type, id),
    CONSTRAINT field_meta_entity_type
        FOREIGN KEY (entity_type)
            REFERENCES entity_type_meta (entity_type)
            ON DELETE CASCADE
            ON UPDATE CASCADE
);

CREATE INDEX field_meta_entity_type ON field_meta(entity_type);
CREATE INDEX field_meta_id ON field_meta(id);

COMMIT;
