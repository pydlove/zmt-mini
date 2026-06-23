CREATE TABLE IF NOT EXISTS tu_style_config (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    strategy VARCHAR(16) NOT NULL,
    params VARCHAR(500),
    is_active INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tu_style_config (id, name, strategy, params, is_active) VALUES
('default', '默认纯视觉美化', 'A', '{}', 1);

CREATE TABLE IF NOT EXISTS tu_user (
    id VARCHAR(64) PRIMARY KEY,
    invite_code VARCHAR(64),
    membership_plan_id VARCHAR(64),
    track_limit INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tu_track (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128),
    is_deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tu_membership_plan (
    id VARCHAR(64) PRIMARY KEY,
    track_limit INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tu_title_library (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(256),
    track_id VARCHAR(64),
    platform VARCHAR(128),
    description VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS tu_email_push_log (
    id VARCHAR(64) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS tu_title_recommendation (
    id VARCHAR(64) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS tu_user_track (
    id VARCHAR(64) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS tu_config (
    id VARCHAR(64) PRIMARY KEY
);
