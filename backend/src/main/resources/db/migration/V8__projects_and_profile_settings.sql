-- PathPilot V8: Projects detail fields, milestone completed_date, worklog/media soft-delete & media table,
-- and user_profiles skills + target_roles JSON fields.

ALTER TABLE projects
    ADD COLUMN icon VARCHAR(40) NULL AFTER title,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_projects_user_active (user_id, deleted_at_utc, updated_at_utc);

ALTER TABLE project_milestones
    ADD COLUMN completed_date DATE NULL AFTER due_date,
    ADD COLUMN notes TEXT NULL AFTER sort_order,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_project_milestones_project_active (user_id, project_id, deleted_at_utc, sort_order);

ALTER TABLE project_worklogs
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_project_worklogs_project_active (user_id, project_id, deleted_at_utc, work_date);

CREATE TABLE project_media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    stored_filename VARCHAR(120) NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NULL,
    file_size_bytes BIGINT NULL,
    video_url VARCHAR(500) NULL,
    caption VARCHAR(255) NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_project_media_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_media_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    INDEX idx_project_media_project (user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE user_profiles
    ADD COLUMN skills_json TEXT NULL AFTER codingbat_url,
    ADD COLUMN target_roles_json TEXT NULL AFTER target_role;
