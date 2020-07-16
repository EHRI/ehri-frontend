-- Change boolean columns temporarily to TYPE smallint to handle MySQL import

BEGIN TRANSACTION;

ALTER TABLE users
    ALTER COLUMN verified DROP DEFAULT,
    ALTER COLUMN staff DROP DEFAULT,
    ALTER COLUMN active DROP DEFAULT,
    ALTER COLUMN allow_messaging DROP DEFAULT,
    ALTER COLUMN is_legacy DROP DEFAULT;

ALTER TABLE users
    ALTER COLUMN verified TYPE smallint 
        USING CASE WHEN verified = TRUE THEN 1 WHEN verified = FALSE THEN 0 ELSE NULL END,
    ALTER COLUMN staff TYPE smallint 
        USING CASE WHEN staff = TRUE THEN 1 WHEN staff = FALSE THEN 0 ELSE NULL END,
    ALTER COLUMN active TYPE smallint 
        USING CASE WHEN active = TRUE THEN 1 WHEN active = FALSE THEN 0 ELSE NULL END,
    ALTER COLUMN allow_messaging TYPE smallint 
        USING CASE WHEN allow_messaging = TRUE THEN 1 WHEN allow_messaging = FALSE THEN 0 ELSE NULL END,
    ALTER COLUMN is_legacy TYPE smallint 
        USING CASE WHEN is_legacy = TRUE THEN 1 WHEN is_legacy = FALSE THEN 0 ELSE NULL END;

ALTER TABLE token
    ALTER COLUMN is_sign_up DROP DEFAULT,
    ALTER COLUMN is_sign_up TYPE smallint 
        USING CASE WHEN is_sign_up = TRUE THEN 1 WHEN is_sign_up = FALSE THEN 0 ELSE NULL END;

ALTER TABLE research_guide
    ALTER COLUMN active DROP DEFAULT,
    ALTER COLUMN active TYPE smallint 
        USING CASE WHEN active = TRUE THEN 1 WHEN active = FALSE THEN 0 ELSE NULL END;

ALTER TABLE feedback
    ALTER COLUMN copy DROP DEFAULT,
    ALTER COLUMN copy TYPE smallint 
        USING CASE WHEN copy = TRUE THEN 1 WHEN copy = FALSE THEN 0 ELSE NULL END;

ALTER TABLE cypher_queries
    ALTER COLUMN public DROP DEFAULT,
    ALTER COLUMN public TYPE smallint 
        USING CASE WHEN public = TRUE THEN 1 WHEN public = FALSE THEN 0 ELSE NULL END;

COMMIT;
