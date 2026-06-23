CREATE TABLE IF NOT EXISTS tu_style_config (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    strategy VARCHAR(16) NOT NULL COMMENT 'A/B/C/D/E/F/G',
    params JSON,
    is_active TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章样式处理全局配置';

INSERT INTO tu_style_config (id, name, strategy, params, is_active) VALUES
('default', '默认纯视觉美化', 'A', '{}', 1)
ON DUPLICATE KEY UPDATE is_active = 1;
