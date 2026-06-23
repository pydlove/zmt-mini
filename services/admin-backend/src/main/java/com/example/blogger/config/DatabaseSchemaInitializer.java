package com.example.blogger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final DataSource dataSource;

    public DatabaseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            ensureColumn(conn, "tu_title_generation_task", "progress_step",
                "ALTER TABLE tu_title_generation_task ADD COLUMN progress_step INT DEFAULT 0 COMMENT '进度步骤：0=排队 1=构建提示词 2=大模型生成 3=写入文件 4=完成'");

            ensureColumn(conn, "tu_title_generation_task", "progress_message",
                "ALTER TABLE tu_title_generation_task ADD COLUMN progress_message VARCHAR(200) DEFAULT '' COMMENT '当前进度描述'");

            ensureColumn(conn, "tu_title_library", "generate_status",
                "ALTER TABLE tu_title_library ADD COLUMN generate_status INT DEFAULT 0 COMMENT '生成状态：0=未生成 1=生成成功 2=生成中'");

            ensureColumn(conn, "tu_title_generation_task", "generated_content",
                "ALTER TABLE tu_title_generation_task ADD COLUMN generated_content LONGTEXT COMMENT '大模型生成的原始内容'");

            ensureColumn(conn, "tu_title_library", "task_id",
                "ALTER TABLE tu_title_library ADD COLUMN task_id VARCHAR(32) DEFAULT NULL COMMENT '关联的生成任务ID'");

            ensureColumn(conn, "tu_user", "theme_color",
                "ALTER TABLE tu_user ADD COLUMN theme_color VARCHAR(20) DEFAULT '#fa541c' COMMENT '文章主题色'");

            ensureColumn(conn, "tu_user", "title_font_size",
                "ALTER TABLE tu_user ADD COLUMN title_font_size INT DEFAULT 16 COMMENT '文章标题字号(pt)'");

            ensureColumn(conn, "tu_user", "content_font_size",
                "ALTER TABLE tu_user ADD COLUMN content_font_size INT DEFAULT 12 COMMENT '文章正文字号(pt)'");

            ensureColumn(conn, "tu_title_library", "is_confirmed",
                "ALTER TABLE tu_title_library ADD COLUMN is_confirmed INT DEFAULT 0 COMMENT '是否确认: 0=未确认 1=已确认'");

            ensureColumn(conn, "tu_title_library", "is_ai_flavor_heavy",
                "ALTER TABLE tu_title_library ADD COLUMN is_ai_flavor_heavy INT DEFAULT 0 COMMENT 'AI味重标记: 0=正常 1=AI味重'");

            ensureColumn(conn, "tu_title_library", "ai_flavor_status",
                "ALTER TABLE tu_title_library ADD COLUMN ai_flavor_status INT DEFAULT 0 COMMENT 'AI味状态: 0/null=未检测 1=已通过 2=AI味重'");

            // 迁移 is_ai_passed / is_ai_flavor_heavy 数据到 ai_flavor_status
            migrateAiFlavorStatus(conn);

            ensureColumn(conn, "tu_title_generation_task", "process_started_at",
                "ALTER TABLE tu_title_generation_task ADD COLUMN process_started_at DATETIME DEFAULT NULL COMMENT '开始生成时间（进入processing状态的时间）'");

            ensureColumn(conn, "tu_title_library", "confirm_status",
                "ALTER TABLE tu_title_library ADD COLUMN confirm_status INT DEFAULT 0 COMMENT '确认状态: 0=未确认 1=已确认 2=已拒绝'");

            // 迁移 is_confirmed 数据到 confirm_status
            migrateConfirmStatus(conn);

            ensureTable(conn, "tu_title_generate_task",
                "CREATE TABLE IF NOT EXISTS tu_title_generate_task (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '任务ID'," +
                "  status VARCHAR(20) DEFAULT 'pending' COMMENT '任务状态'," +
                "  platforms VARCHAR(500) DEFAULT NULL COMMENT '平台列表JSON'," +
                "  track_ids VARCHAR(500) DEFAULT NULL COMMENT '赛道ID列表JSON'," +
                "  count_per_combo INT DEFAULT 3 COMMENT '每个组合生成数量'," +
                "  instruction VARCHAR(1000) DEFAULT NULL COMMENT '生成方向'," +
                "  result_file_url VARCHAR(255) DEFAULT NULL COMMENT '结果Excel文件URL'," +
                "  result_file_name VARCHAR(255) DEFAULT NULL COMMENT '结果Excel文件名'," +
                "  error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因'," +
                "  progress_step INT DEFAULT 0 COMMENT '进度步骤'," +
                "  progress_message VARCHAR(200) DEFAULT '' COMMENT '当前进度描述'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  processed_at DATETIME DEFAULT NULL COMMENT '完成时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V2标题生成任务表'");

            ensureTable(conn, "tu_llm_config",
                "CREATE TABLE IF NOT EXISTS tu_llm_config (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  provider VARCHAR(20) NOT NULL COMMENT '提供商: kimi/minimax'," +
                "  api_key VARCHAR(255) DEFAULT NULL COMMENT 'API Key'," +
                "  model VARCHAR(50) DEFAULT NULL COMMENT '模型名称'," +
                "  is_active TINYINT DEFAULT 0 COMMENT '是否当前选中: 0=否 1=是'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE KEY uk_provider (provider)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型配置表'");

            ensureTable(conn, "tu_prompt_template",
                "CREATE TABLE IF NOT EXISTS tu_prompt_template (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '模板ID'," +
                "  name VARCHAR(100) NOT NULL COMMENT '模板名称'," +
                "  content LONGTEXT NOT NULL COMMENT '提示词内容'," +
                "  type VARCHAR(50) DEFAULT 'generate_title' COMMENT '类别: generate_title=生成标题'," +
                "  is_default TINYINT DEFAULT 0 COMMENT '是否默认: 0=否 1=是'," +
                "  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0=否 1=是'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表'");

            ensureTable(conn, "tu_image_library",
                "CREATE TABLE IF NOT EXISTS tu_image_library (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '图片ID'," +
                "  name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名'," +
                "  url VARCHAR(500) NOT NULL COMMENT '图片访问URL'," +
                "  categories VARCHAR(500) DEFAULT NULL COMMENT '赛道ID列表JSON'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片库表'");

            ensureTable(conn, "tu_announcement",
                "CREATE TABLE IF NOT EXISTS tu_announcement (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '公告ID'," +
                "  type VARCHAR(50) NOT NULL COMMENT '公告类型: article_push=文章推送公告'," +
                "  content TEXT NOT NULL COMMENT '公告内容（支持HTML）'," +
                "  is_enabled INT NOT NULL DEFAULT 1 COMMENT '是否开启: 0=关闭 1=开启'," +
                "  is_deleted INT NOT NULL DEFAULT 0 COMMENT '是否删除: 0=否 1=是'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  UNIQUE KEY uk_type (type)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告管理表'");

            ensureColumn(conn, "tu_title_generate_task", "duplicate_count",
                "ALTER TABLE tu_title_generate_task ADD COLUMN duplicate_count INT DEFAULT 0 COMMENT '重复标题数量'");

            ensureColumn(conn, "tu_title_generate_task", "inserted_count",
                "ALTER TABLE tu_title_generate_task ADD COLUMN inserted_count INT DEFAULT 0 COMMENT '成功插入标题数量'");

            ensureColumn(conn, "tu_image_library", "updated_at",
                "ALTER TABLE tu_image_library ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");

            ensureColumn(conn, "tu_title_library", "image_post_urls",
                "ALTER TABLE tu_title_library ADD COLUMN image_post_urls TEXT COMMENT '贴图URL列表，JSON数组字符串'");

            // 兼容旧版本：如果 image_post_urls 还是 VARCHAR(2000)，升级为 TEXT
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE tu_title_library MODIFY COLUMN image_post_urls TEXT COMMENT '贴图URL列表，JSON数组字符串'");
                log.info("[DatabaseSchemaInitializer] 已调整 image_post_urls 字段类型为 TEXT");
            } catch (Exception e) {
                log.debug("[DatabaseSchemaInitializer] 调整 image_post_urls 字段类型跳过: {}", e.getMessage());
            }

            ensureColumn(conn, "tu_title_library", "banned_word_check_result",
                "ALTER TABLE tu_title_library ADD COLUMN banned_word_check_result JSON NULL COMMENT '违禁词检测结果'");

            ensureColumn(conn, "tu_title_library", "title_keyword",
                "ALTER TABLE tu_title_library ADD COLUMN title_keyword VARCHAR(255) DEFAULT NULL COMMENT '标题分词关键词，用于相似度检测'");

            // 兼容：title_keyword 可能长度不够，升级为 TEXT
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE tu_title_library MODIFY COLUMN title_keyword TEXT COMMENT '标题分词关键词，用于相似度检测'");
                log.info("[DatabaseSchemaInitializer] 已调整 title_keyword 字段类型为 TEXT");
            } catch (Exception e) {
                log.debug("[DatabaseSchemaInitializer] 调整 title_keyword 字段类型跳过: {}", e.getMessage());
            }

            ensureColumn(conn, "tu_title_generate_task", "style_template_id",
                "ALTER TABLE tu_title_generate_task ADD COLUMN style_template_id VARCHAR(32) DEFAULT NULL COMMENT '选中的标题风格模板ID'");

            // 用户同质化程度表
            ensureUserHomogeneityTable(conn);

            // Agent 配置表和执行记录表
            ensureAgentTables(conn);

            // 写作风格库表
            ensureTable(conn, "tu_writing_style",
                "CREATE TABLE IF NOT EXISTS tu_writing_style (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '记录ID'," +
                "  original_word VARCHAR(100) NOT NULL COMMENT '原词'," +
                "  style_word VARCHAR(100) NOT NULL COMMENT '风格替换词'," +
                "  category VARCHAR(50) DEFAULT '通用' COMMENT '分类'," +
                "  is_active TINYINT DEFAULT 1 COMMENT '是否启用: 0=禁用 1=启用'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作风格词库'");

            // 标题禁用词库表
            ensureTable(conn, "tu_title_banned_word",
                "CREATE TABLE IF NOT EXISTS tu_title_banned_word (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '记录ID'," +
                "  word VARCHAR(100) NOT NULL COMMENT '禁用词'," +
                "  category VARCHAR(50) DEFAULT '通用' COMMENT '分类'," +
                "  is_active TINYINT DEFAULT 1 COMMENT '是否启用: 0=禁用 1=启用'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标题禁用词库'");

            // 文章样式策略配置表（A-G 渲染策略）
            ensureTable(conn, "tu_style_config",
                "CREATE TABLE IF NOT EXISTS tu_style_config (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '配置ID'," +
                "  name VARCHAR(100) NOT NULL COMMENT '策略名称'," +
                "  strategy VARCHAR(10) NOT NULL COMMENT '策略: A=纯视觉美化 B=自动连续编号 C=模板映射 D=下游差异化 E=内容分级 F=自动生成目录 G=AI配图提示'," +
                "  params JSON DEFAULT NULL COMMENT '策略参数（JSON）'," +
                "  is_active TINYINT DEFAULT 0 COMMENT '是否当前激活: 0=否 1=是'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章样式策略配置表'");

            // 初始化默认激活策略：纯视觉美化（A）
            ensureDefaultStyleConfig(conn);

            // 文章导出模板表
            ensureTable(conn, "tu_export_template",
                "CREATE TABLE IF NOT EXISTS tu_export_template (" +
                "  id VARCHAR(32) PRIMARY KEY COMMENT '模板ID'," +
                "  name VARCHAR(100) NOT NULL COMMENT '模板名称'," +
                "  type VARCHAR(50) DEFAULT 'docx' COMMENT '模板类型'," +
                "  config JSON NOT NULL COMMENT '样式配置（JSON）'," +
                "  is_default TINYINT DEFAULT 0 COMMENT '是否默认: 0=否 1=是'," +
                "  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0=否 1=是'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  UNIQUE KEY uk_name (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章导出模板配置表'");

            // 初始化内置导出模板
            ensureDefaultExportTemplates(conn);

            // 初始化标题风格提示词模板
            initTitleStyleTemplates(conn);
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 数据库连接失败: {}", e.getMessage(), e);
        }
    }

    private void ensureAgentTables(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // tu_agent_config
            boolean configExists = false;
            try (ResultSet rs = metaData.getTables(catalog, null, "tu_agent_config", null)) {
                configExists = rs.next();
            }
            if (!configExists) {
                String createConfigSql = "CREATE TABLE IF NOT EXISTS tu_agent_config (" +
                    "  id BIGINT PRIMARY KEY COMMENT '配置ID，固定为1'," +
                    "  enabled TINYINT DEFAULT 0 COMMENT '是否启用: 0=禁用 1=启用'," +
                    "  cron_expr VARCHAR(50) DEFAULT '0 0 6 * * ?' COMMENT '定时表达式'," +
                    "  similarity_threshold DECIMAL(3,2) DEFAULT 0.15 COMMENT '相似度阈值'," +
                    "  homogeneity_threshold DECIMAL(3,2) DEFAULT 0.15 COMMENT '同质化阈值'," +
                    "  min_titles_per_track INT DEFAULT 5 COMMENT '每赛道最少推荐数'," +
                    "  history_days INT DEFAULT 30 COMMENT '历史标题天数'," +
                    "  candidate_limit INT DEFAULT 50 COMMENT '候选标题上限'," +
                    "  max_generation_concurrency INT DEFAULT 3 COMMENT '文章生成并发数'," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 配置表'";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createConfigSql);
                    log.info("[DatabaseSchemaInitializer] 已自动创建表: tu_agent_config");
                }
            }

            // tu_agent_execution
            boolean executionExists = false;
            try (ResultSet rs = metaData.getTables(catalog, null, "tu_agent_execution", null)) {
                executionExists = rs.next();
            }
            if (!executionExists) {
                String createExecutionSql = "CREATE TABLE IF NOT EXISTS tu_agent_execution (" +
                    "  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行记录ID'," +
                    "  execution_date DATE NOT NULL COMMENT '执行日期'," +
                    "  status VARCHAR(20) DEFAULT 'running' COMMENT '状态: running/completed/failed/partial'," +
                    "  total_users INT DEFAULT 0 COMMENT '总处理用户数'," +
                    "  total_tracks INT DEFAULT 0 COMMENT '总处理赛道数'," +
                    "  matched_titles INT DEFAULT 0 COMMENT '匹配标题数'," +
                    "  generated_titles INT DEFAULT 0 COMMENT '生成标题数'," +
                    "  article_tasks INT DEFAULT 0 COMMENT '文章任务数'," +
                    "  failed_count INT DEFAULT 0 COMMENT '失败数'," +
                    "  detail_json LONGTEXT COMMENT '执行详情JSON'," +
                    "  started_at DATETIME DEFAULT NULL COMMENT '开始时间'," +
                    "  completed_at DATETIME DEFAULT NULL COMMENT '完成时间'," +
                    "  error_message TEXT COMMENT '错误信息'," +
                    "  KEY idx_execution_date (execution_date)," +
                    "  KEY idx_status (status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 执行记录表'";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createExecutionSql);
                    log.info("[DatabaseSchemaInitializer] 已自动创建表: tu_agent_execution");
                }
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 创建 Agent 表失败: {}", e.getMessage());
        }
    }

    private void ensureUserHomogeneityTable(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean exists = false;
            String catalog = conn.getCatalog();
            try (ResultSet rs = metaData.getTables(catalog, null, "tu_user_homogeneity", null)) {
                exists = rs.next();
            }
            if (!exists) {
                String createSql = "CREATE TABLE IF NOT EXISTS tu_user_homogeneity (" +
                    "  id VARCHAR(32) PRIMARY KEY COMMENT '记录ID'," +
                    "  user_id VARCHAR(32) NOT NULL COMMENT '用户ID'," +
                    "  homogeneity_score INT DEFAULT 0 COMMENT '同质化程度 0-100'," +
                    "  history_count INT DEFAULT 0 COMMENT '参与计算的历史标题数量'," +
                    "  calculated_at DATETIME DEFAULT NULL COMMENT '计算时间'," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                    "  UNIQUE KEY uk_user_id (user_id)," +
                    "  KEY idx_calculated_at (calculated_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户同质化程度计算结果'";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createSql);
                    log.info("[DatabaseSchemaInitializer] 已自动创建表: tu_user_homogeneity");
                }
            } else {
                log.debug("[DatabaseSchemaInitializer] 表 tu_user_homogeneity 已存在，跳过创建");
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 创建表 tu_user_homogeneity 失败: {}", e.getMessage());
        }
    }

    private void ensureTable(Connection conn, String tableName, String createSql) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean exists = false;
            String catalog = conn.getCatalog();
            try (ResultSet rs = metaData.getTables(catalog, null, tableName, null)) {
                exists = rs.next();
            }
            if (!exists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createSql);
                    log.info("[DatabaseSchemaInitializer] 已自动创建表: {}", tableName);
                }
                // 创建索引
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE INDEX idx_tg_task_status ON " + tableName + "(status)");
                    stmt.executeUpdate("CREATE INDEX idx_tg_task_created_at ON " + tableName + "(created_at)");
                }
            } else {
                log.debug("[DatabaseSchemaInitializer] 表 {} 已存在，跳过创建", tableName);
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 创建表 {} 失败: {}", tableName, e.getMessage());
        }
    }

    private void migrateConfirmStatus(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "UPDATE tu_title_library SET confirm_status = 1 WHERE is_confirmed = 1 AND (confirm_status IS NULL OR confirm_status = 0)"
            );
            log.info("[DatabaseSchemaInitializer] 已迁移 is_confirmed 数据到 confirm_status");
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 迁移 confirm_status 失败: {}", e.getMessage());
        }
    }

    private void migrateAiFlavorStatus(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "UPDATE tu_title_library SET ai_flavor_status = 2 WHERE is_ai_flavor_heavy = 1 AND ai_flavor_status IS NULL"
            );
            stmt.executeUpdate(
                "UPDATE tu_title_library SET ai_flavor_status = 1 WHERE is_ai_passed = 1 AND (is_ai_flavor_heavy IS NULL OR is_ai_flavor_heavy != 1) AND ai_flavor_status IS NULL"
            );
            log.info("[DatabaseSchemaInitializer] 已迁移 AI味状态数据到 ai_flavor_status");
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 迁移 ai_flavor_status 失败: {}", e.getMessage());
        }
    }

    private void initTitleStyleTemplates(Connection conn) {
        try {
            // 检查是否已有 title_style 类型的模板
            boolean exists = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tu_prompt_template WHERE type = 'title_style' AND is_deleted = 0")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }
            if (exists) {
                log.debug("[DatabaseSchemaInitializer] title_style 模板已存在，跳过初始化");
                return;
            }

            String[][] templates = {
                {"叙事型", "标题采用真实场景叙事风格，像朋友间分享故事一样自然。要求：使用具体的时间/地点/动作细节；带有轻微的情感转折；不刻意制造悬念，让读者从标题就能感受到真实感。严禁使用数字列表、感叹号、震惊体词汇。"},
                {"数据型", "标题以具体数据或调研结果为核心支撑。要求：使用精确的数字（避免整数，可带小数点或范围）；数据来源感强；突出「发现」或「真相」的揭示感。严禁使用「震惊」「绝了」等夸张情绪词。"},
                {"反转型", "标题先建立读者的常规认知，再给出打破认知的结论。要求：前半句是大众普遍认同的观点，后半句是反常识的转折；使用「其实」「才发现」「错了」等转折词；制造温和的认知冲突。"},
                {"身份型", "标题精准锁定特定读者群体的身份标签。要求：直接使用「如果你是...」「那些...的人」等身份定位句式；点出读者的痛点或隐秘需求；让读者产生「说的就是我」的代入感。"},
                {"场景型", "标题描绘一个具体画面或场景，让读者瞬间代入。要求：使用视觉化、感官化的描写；带有时间或环境细节（如「凌晨2点」「医院走廊」）；画面本身蕴含情感张力，不靠解释。"},
                {"对话型", "标题模拟日常对话，语气轻松自然。要求：使用第一人称或第二人称；像微信聊天一样口语化；可带轻微的吐槽或自嘲语气。"},
            };

            for (String[] tpl : templates) {
                String id = java.util.UUID.randomUUID().toString().replace("-", "");
                String sql = "INSERT INTO tu_prompt_template(id, name, content, type, is_default, is_deleted, created_at) " +
                    "VALUES('" + id + "', '" + tpl[0] + "', '" + tpl[1] + "', 'title_style', 0, 0, NOW())";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            }
            log.info("[DatabaseSchemaInitializer] 已初始化 {} 种标题风格模板", templates.length);
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 初始化标题风格模板失败: {}", e.getMessage());
        }
    }

    /**
     * 初始化默认激活的样式策略（策略 A 纯视觉美化）。
     * <p>
     * 仅当表中无任何激活记录时插入一条默认配置，避免每次启动都覆盖用户已配置的策略。
     */
    private void ensureDefaultStyleConfig(Connection conn) {
        try {
            boolean hasActive = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tu_style_config WHERE is_active = 1")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    hasActive = true;
                }
            }
            if (hasActive) {
                log.debug("[DatabaseSchemaInitializer] 已存在激活的样式策略，跳过默认初始化");
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                    "INSERT INTO tu_style_config(id, name, strategy, params, is_active, created_at, updated_at) " +
                    "VALUES('DEFAULT', '默认纯视觉美化', 'A', NULL, 1, NOW(), NOW())"
                );
                log.info("[DatabaseSchemaInitializer] 已初始化默认样式策略: DEFAULT / 策略 A");
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 初始化默认样式策略失败: {}", e.getMessage());
        }
    }

    /**
     * 同步内置导出模板。
     * <p>
     * 按 fixed ID 插入或更新 12 套预设模板，确保与原型 full-prototype-v20.html 保持一致。
     * 用户自定义模板使用不同 ID，不会被覆盖。
     */
    private void ensureDefaultExportTemplates(Connection conn) {
        try {
            String[][] templates = {
                {"tpl_gh_std", "公众号标准模板", "1", "#1a1a1a", "#07c160", "#f6ffed",
                 "微软雅黑", "微软雅黑", "12", "14", "#262626", "432", "200", "1800", "1800",
                 "16px 正文 / 18px 小标题 / 绿色强调"},
                {"tpl_toutiao", "今日头条模板", "0", "#ff6600", "#ff6600", "#fff7e6",
                 "微软雅黑", "微软雅黑", "13", "14", "#222222", "432", "200", "1440", "1440",
                 "17px 正文 / 橙色强调 / 资讯感标题"},
                {"tpl_xiaohongshu", "小红书图文模板", "0", "#ff2442", "#ff2442", "#fff0f3",
                 "PingFang SC", "PingFang SC", "11", "13", "#333333", "444", "200", "1440", "1440",
                 "15px 正文 / 粉红标签 / 轻松活泼"},
                {"tpl_baijiahao", "百家号模板", "0", "#1677ff", "#1677ff", "#e6f4ff",
                 "微软雅黑", "微软雅黑", "12", "14", "#262626", "432", "180", "1440", "1440",
                 "16px 正文 / 蓝色层级 / 信息密度高"},
                {"tpl_business", "简约商务模板", "0", "#1677ff", "#1677ff", "#f0f5ff",
                 "微软雅黑", "微软雅黑", "11", "12", "#262626", "420", "180", "1800", "1800",
                 "14px 正文 / 深蓝标题 / 清晰层级"},
                {"tpl_marketing", "营销转化模板", "0", "#cf1322", "#cf1322", "#fff2f0",
                 "PingFang SC", "PingFang SC", "14", "15", "#262626", "432", "220", "1440", "1440",
                 "18px 正文 / 红色强调 / 引导行动"},
                {"tpl_story", "故事叙事模板", "0", "#5a3e2b", "#8b5e34", "#faf5ef",
                 "Georgia", "Georgia", "12", "14", "#262626", "444", "220", "1800", "1800",
                 "16px 正文 / 暖棕标题 / 沉浸阅读"},
                {"tpl_academic", "学术报告模板", "0", "#1a1a1a", "#333333", "#fafafa",
                 "宋体", "黑体", "12", "13", "#262626", "360", "160", "1800", "1800",
                 "宋体 / 1.5 倍行距 / 自动编号"},
                {"tpl_magazine", "杂志大字模板", "0", "#1a1a1a", "#1a1a1a", "#fafafa",
                 "Georgia", "Georgia", "12", "14", "#262626", "456", "240", "1800", "1800",
                 "大标题居中 / 衬线字体 / 留白呼吸"},
                {"tpl_card", "卡片分块模板", "0", "#07c160", "#07c160", "#ffffff",
                 "微软雅黑", "微软雅黑", "11", "13", "#262626", "432", "200", "1440", "1440",
                 "分块卡片 / 阴影层级 / 信息聚焦"},
                {"tpl_checklist", "极简清单模板", "0", "#07c160", "#07c160", "#f6ffed",
                 "微软雅黑", "微软雅黑", "11", "13", "#262626", "432", "180", "1800", "1800",
                 "清单体 / 勾选符号 / 行动导向"},
                {"tpl_dark", "深色沉浸模板", "0", "#95de64", "#07c160", "#333333",
                 "微软雅黑", "微软雅黑", "12", "14", "#f0f0f0", "432", "200", "1800", "1800",
                 "深色背景 / 高对比 / 沉浸阅读"}
            };
            String insertSql = "INSERT INTO tu_export_template(id, name, type, config, is_default, is_deleted, created_at, updated_at) " +
                "VALUES(?, ?, 'docx', ?, ?, 0, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "name = VALUES(name), type = VALUES(type), config = VALUES(config), " +
                "is_default = VALUES(is_default), is_deleted = VALUES(is_deleted), updated_at = NOW()";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (String[] t : templates) {
                    String config = "{" +
                        "\"fontFamily\":\"" + t[6] + "\"," +
                        "\"headingFontFamily\":\"" + t[7] + "\"," +
                        "\"bodyFontSizePt\":" + t[8] + "," +
                        "\"headingFontSizePt\":" + t[9] + "," +
                        "\"bodyColor\":\"" + t[10] + "\"," +
                        "\"headingColor\":\"" + t[3] + "\"," +
                        "\"lineSpacing\":" + t[11] + "," +
                        "\"paragraphSpacingAfter\":" + t[12] + "," +
                        "\"marginTop\":1440," +
                        "\"marginBottom\":1440," +
                        "\"marginLeft\":" + t[13] + "," +
                        "\"marginRight\":" + t[14] + "," +
                        "\"quoteBg\":\"" + t[5] + "\"," +
                        "\"previewColor\":\"" + t[4] + "\"," +
                        "\"description\":\"" + t[15] + "\"" +
                        "}";
                    ps.setString(1, t[0]);
                    ps.setString(2, t[1]);
                    ps.setString(3, config);
                    ps.setInt(4, Integer.parseInt(t[2]));
                    ps.executeUpdate();
                }
                log.info("[DatabaseSchemaInitializer] 已同步 {} 套内置导出模板", templates.length);
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 同步内置导出模板失败: {}", e.getMessage(), e);
        }
    }

    private void ensureColumn(Connection conn, String tableName, String columnName, String alterSql) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean exists = false;
            String catalog = conn.getCatalog();
            try (ResultSet rs = metaData.getColumns(catalog, null, tableName, columnName)) {
                exists = rs.next();
            }
            if (!exists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(alterSql);
                    log.info("[DatabaseSchemaInitializer] 已自动添加字段: {}.{}", tableName, columnName);
                }
            } else {
                log.debug("[DatabaseSchemaInitializer] {}.{} 已存在，跳过迁移", tableName, columnName);
            }
        } catch (Exception e) {
            log.error("[DatabaseSchemaInitializer] 添加字段 {}.{} 失败: {}", tableName, columnName, e.getMessage());
        }
    }
}
