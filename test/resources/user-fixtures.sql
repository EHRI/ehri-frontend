INSERT INTO users (id, email, verified, staff)
VALUES ('mike', 'example1@example.com', true, true),
       ('reto', 'example2@example.com', true, true),
       ('linda', 'example3@example.com', true, true),
       ('joeblogs', 'example@aol.com', true, false),
       ('bobjohn', 'example@yahoo.com', false, false);

INSERT INTO oauth2_association(id, provider_id, provider)
VALUES ('mike', '123456789', 'google'),
       ('reto', '123456789', 'facebook');

INSERT INTO openid_association(id, openid_url)
VALUES ('linda', 'https://yahoo.com/openid');
