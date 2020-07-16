ALTER TABLE research_guide CHANGE `default` default_page INTEGER(11) NULL;
ALTER TABLE research_guide CHANGE active active BOOLEAN DEFAULT TRUE;
ALTER TABLE research_guide_page CHANGE research_guide_id research_guide_id INTEGER(11) NOT NULL;
ALTER TABLE openid_association CHANGE created created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE oauth2_association CHANGE created created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;