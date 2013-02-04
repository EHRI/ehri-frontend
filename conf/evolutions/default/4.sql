# --- !Ups

ALTER TABLE openid_association
    ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES openid_user (id) ON DELETE CASCADE ;
ALTER TABLE user_auth
    ADD CONSTRAINT user_id_fk FOREIGN KEY (id) REFERENCES openid_user (id) ON DELETE CASCADE ;

# --- !Downs

ALTER TABLE openid_association
    DROP CONSTRAINT user_id_fk;
ALTER TABLE user_auth
    DROP CONSTRAINT user_id_fk;

