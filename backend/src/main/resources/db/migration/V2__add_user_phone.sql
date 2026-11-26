ALTER TABLE users
    ADD COLUMN phone VARCHAR(20) NOT NULL AFTER email,
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);
