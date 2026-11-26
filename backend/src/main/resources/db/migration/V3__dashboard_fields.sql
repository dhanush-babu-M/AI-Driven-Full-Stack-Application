ALTER TABLE tasks
    ADD COLUMN priority INT NOT NULL DEFAULT 2 AFTER category,
    ADD COLUMN duration_minutes INT NULL AFTER priority;

ALTER TABLE user_profiles
    ADD COLUMN portfolio_url VARCHAR(500) NULL AFTER bio,
    ADD COLUMN github_url VARCHAR(500) NULL AFTER portfolio_url,
    ADD COLUMN linkedin_url VARCHAR(500) NULL AFTER github_url,
    ADD COLUMN coding_profile_url VARCHAR(500) NULL AFTER linkedin_url;
