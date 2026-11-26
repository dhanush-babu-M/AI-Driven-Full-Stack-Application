ALTER TABLE tasks
    ADD COLUMN start_time TIME NULL AFTER task_date,
    ADD COLUMN end_time TIME NULL AFTER start_time,
    ADD COLUMN notes TEXT NULL AFTER duration_minutes,
    ADD COLUMN carried_from_task_id BIGINT NULL AFTER notes,
    ADD COLUMN rescheduled_to_date DATE NULL AFTER carried_from_task_id,
    ADD COLUMN rescheduled_to_task_id BIGINT NULL AFTER rescheduled_to_date,
    ADD COLUMN reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER rescheduled_to_task_id,
    ADD COLUMN reminder_time TIME NULL AFTER reminder_enabled,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_tasks_user_active_date (user_id, deleted_at_utc, task_date);
