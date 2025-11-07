BEGIN TRANSACTION;

CREATE TABLE application_event(
    id      UUID NOT NULL PRIMARY KEY,
    data    TEXT,
    name    VARCHAR(50) NOT NULL,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX application_event_id ON application_event(id);
CREATE INDEX application_event_name ON application_event(name);

COMMIT;
