BEGIN TRANSACTION;

CREATE TABLE lifecycle_event(
    data TEXT,
    id   UUID NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE INDEX lifecycle_event_id ON lifecycle_event(id);
CREATE INDEX lifecycle_event_name ON lifecycle_event(name);

COMMIT;
