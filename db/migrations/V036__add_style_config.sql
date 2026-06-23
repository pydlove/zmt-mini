CREATE TABLE IF NOT EXISTS tu_style_config (
    id VARCHAR(32) PRIMARY KEY COMMENT '配置ID',
    name VARCHAR(100) NOT NULL COMMENT '策略名称',
    strategy VARCHAR(10) NOT NULL COMMENT '策略: A=纯视觉美化 B=自动连续编号 C=模板映射 D=下游差异化 E=内容分级 F=自动生成目录 G=AI配图提示',
    params JSON DEFAULT NULL COMMENT '策略参数（JSON）',
    is_active TINYINT DEFAULT 0 COMMENT '是否当前激活: 0=否 1=是',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章样式策略配置表';

-- 初始化默认激活策略：纯视觉美化（A），仅在无激活记录时插入
INSERT INTO tu_style_config(id, name, strategy, params, is_active, created_at, updated_at)
SELECT 'DEFAULT', '默认纯视觉美化', 'A', NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tu_style_config WHERE is_active = 1);