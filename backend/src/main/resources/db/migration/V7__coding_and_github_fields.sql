ALTER TABLE user_profiles
    ADD COLUMN github_username VARCHAR(100) NULL AFTER github_url,
    ADD COLUMN leetcode_url VARCHAR(500) NULL AFTER coding_profile_url,
    ADD COLUMN codingbat_url VARCHAR(500) NULL AFTER leetcode_url;

ALTER TABLE connected_accounts
    ADD COLUMN encrypted_token TEXT NULL AFTER sync_enabled,
    ADD COLUMN etag VARCHAR(255) NULL AFTER encrypted_token,
    ADD COLUMN last_sync_status VARCHAR(30) NULL AFTER last_synced_at_utc,
    ADD COLUMN last_sync_error TEXT NULL AFTER last_sync_status;

ALTER TABLE coding_activities
    ADD COLUMN topic VARCHAR(80) NULL AFTER problem_title,
    ADD COLUMN difficulty VARCHAR(20) NULL AFTER topic,
    ADD COLUMN result VARCHAR(20) NOT NULL DEFAULT 'Solved' AFTER difficulty,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_coding_activities_user_active_date (user_id, deleted_at_utc, activity_date);

ALTER TABLE github_events
    ADD COLUMN commit_count INT NOT NULL DEFAULT 0 AFTER payload_summary,
    ADD COLUMN html_url VARCHAR(500) NULL AFTER commit_count;
