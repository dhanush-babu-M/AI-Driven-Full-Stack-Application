-- PathPilot V9: Job Applications fields, status history, and follow-ups with reminder support.

ALTER TABLE job_applications
    ADD COLUMN job_description TEXT NULL AFTER job_url,
    ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'OTHER' AFTER location,
    ADD COLUMN recruiter_notes TEXT NULL AFTER notes,
    ADD COLUMN followup_date DATE NULL AFTER recruiter_notes,
    ADD COLUMN interview_date DATE NULL AFTER followup_date,
    ADD COLUMN prep_checklist_json TEXT NULL AFTER interview_date,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_job_applications_user_active (user_id, deleted_at_utc, applied_date);

CREATE TABLE job_followups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_application_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at_utc DATETIME(6) NULL,
    reminder_time TIME NOT NULL DEFAULT '09:00:00',
    reminder_at_utc DATETIME(6) NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at_utc DATETIME(6) NULL,
    CONSTRAINT fk_job_followups_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_followups_job FOREIGN KEY (job_application_id) REFERENCES job_applications (id) ON DELETE CASCADE,
    INDEX idx_job_followups_user_due (user_id, deleted_at_utc, status, due_date),
    INDEX idx_job_followups_reminder (user_id, deleted_at_utc, status, reminder_at_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
