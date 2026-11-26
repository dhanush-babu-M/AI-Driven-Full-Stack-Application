ALTER TABLE tasks
    ADD COLUMN reminder_at_utc DATETIME(6) NULL AFTER reminder_time,
    ADD INDEX idx_tasks_user_reminder_utc (user_id, deleted_at_utc, reminder_enabled, reminder_at_utc);

ALTER TABLE notification_preferences
    ADD COLUMN browser_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER monthly_report_ready_enabled,
    ADD COLUMN lead_minutes INT NOT NULL DEFAULT 10 AFTER browser_enabled,
    ADD COLUMN quiet_hours_start TIME NULL AFTER lead_minutes,
    ADD COLUMN quiet_hours_end TIME NULL AFTER quiet_hours_start;
