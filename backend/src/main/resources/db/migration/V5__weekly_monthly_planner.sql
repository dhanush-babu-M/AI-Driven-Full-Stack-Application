ALTER TABLE goals
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'OTHER' AFTER title,
    ADD COLUMN target_count INT NOT NULL DEFAULT 1 AFTER category,
    ADD COLUMN done_count INT NOT NULL DEFAULT 0 AFTER target_count,
    ADD COLUMN carried_from_goal_id BIGINT NULL AFTER status,
    ADD COLUMN deleted_at_utc DATETIME(6) NULL AFTER updated_at_utc,
    ADD INDEX idx_goals_user_period (user_id, deleted_at_utc, period_type, start_date);

CREATE TABLE weekly_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    what_went_well TEXT NULL,
    carry_forward_notes TEXT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_weekly_reviews_user_week UNIQUE (user_id, week_start_date),
    CONSTRAINT fk_weekly_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_weekly_reviews_user_week (user_id, week_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
