CREATE DATABASE IF NOT EXISTS blogger_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE blogger_db;

-- Drop old tables if they exist
DROP TABLE IF EXISTS creation_record;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS blogger;
DROP TABLE IF EXISTS track;
DROP TABLE IF EXISTS tu_creation_record;
DROP TABLE IF EXISTS tu_post;
DROP TABLE IF EXISTS tu_blogger;
DROP TABLE IF EXISTS tu_track;
DROP TABLE IF EXISTS ta_user;
DROP TABLE IF EXISTS tu_user;
DROP TABLE IF EXISTS tu_config;

CREATE TABLE tu_config (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value LONGTEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE tu_export_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) DEFAULT 'docx',
    config JSON NOT NULL,
    is_default TINYINT DEFAULT 0,
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
);

CREATE TABLE tu_track (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '',
    sort_order INT DEFAULT 0,
    preview_bloggers VARCHAR(500) DEFAULT '',
    intro VARCHAR(500) DEFAULT '',
    platforms VARCHAR(100) DEFAULT '公众号',
    cover_json LONGTEXT,
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE tu_blogger (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    avatar LONGTEXT,
    tagline VARCHAR(255) DEFAULT '',
    track_id VARCHAR(64) NOT NULL,
    rank_num INT DEFAULT 0,
    link VARCHAR(1000) DEFAULT '',
    platform VARCHAR(50) DEFAULT '公众号',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_track_id (track_id)
);

CREATE TABLE tu_post (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(1000) DEFAULT '',
    blogger_id VARCHAR(64) NOT NULL,
    content LONGTEXT,
    platform VARCHAR(50) DEFAULT '公众号',
    summary VARCHAR(500) DEFAULT '',
    tag VARCHAR(50) DEFAULT '',
    `reads` VARCHAR(20) DEFAULT '',
    `likes` VARCHAR(20) DEFAULT '',
    `comments` VARCHAR(20) DEFAULT '',
    metrics_json VARCHAR(1000) DEFAULT '',
    status VARCHAR(20) DEFAULT '已上架',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_blogger_id (blogger_id)
);

CREATE TABLE tu_creation_record (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    track_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    content LONGTEXT,
    reviewed TINYINT DEFAULT 0 COMMENT '0=未审阅, 1=已审阅',
    mode VARCHAR(20) DEFAULT '半人工',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_track_id (track_id),
    INDEX idx_created_at (created_at)
);

CREATE TABLE ta_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '1=正常, 0=禁用',
    phone VARCHAR(20) DEFAULT '',
    email VARCHAR(100) DEFAULT '',
    wx_id VARCHAR(100) DEFAULT '',
    ai_limit INT DEFAULT 50,
    track_limit INT DEFAULT 0,
    platform_limit VARCHAR(100) DEFAULT '',
    avatar LONGTEXT,
    template VARCHAR(50) DEFAULT '公众号标准模板',
    expire_date DATE DEFAULT '2026-12-31',
    last_login TIMESTAMP NULL DEFAULT NULL,
    remark VARCHAR(500) DEFAULT '',
    name VARCHAR(100) DEFAULT '',
    role VARCHAR(50) DEFAULT '',
    can_set_email TINYINT DEFAULT 0 COMMENT '0=不允许, 1=允许设置邮箱接收文章',
    email_receive TINYINT DEFAULT 0 COMMENT '0=不接收, 1=接收邮件推送',
    invite_code VARCHAR(20) NULL COMMENT '用户邀请码，唯一',
    invited_by VARCHAR(64) NULL COMMENT '邀请人用户ID',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE tu_user LIKE ta_user;

CREATE TABLE tu_user_track (
    user_id VARCHAR(64) NOT NULL,
    track_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, track_id),
    INDEX idx_track_id (track_id)
);

CREATE TABLE ta_role (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    permissions VARCHAR(500) DEFAULT '[]',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Seed data: admin user (ta_user)
INSERT INTO ta_user (id, username, password, status, phone, email, wx_id, ai_limit, expire_date, remark, name, role) VALUES
('admin001', 'admin', 'Abc123456', 1, '13800138000', 'admin@aicloud.com', 'wx_admin', 9999, '2026-12-31', '系统管理员', '系统管理员', '超级管理员');

-- Seed data: roles
INSERT INTO ta_role (id, name, permissions) VALUES
('role001', '超级管理员', '["all"]'),
('role002', '内容管理员', '["track","blogger","post","guide","help"]'),
('role003', '运营管理员', '["user","dashboard","creation-review"]');

-- Seed data: export templates (12 presets, matching full-prototype-v20.html)
INSERT INTO tu_export_template (id, name, type, config, is_default, is_deleted) VALUES
('tpl_gh_std', '公众号标准模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":12,"headingFontSizePt":14,"bodyColor":"#262626","headingColor":"#1a1a1a","lineSpacing":432,"paragraphSpacingAfter":200,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#f6ffed","previewColor":"#07c160","description":"16px 正文 / 18px 小标题 / 绿色强调"}', 1, 0),
('tpl_toutiao', '今日头条模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":13,"headingFontSizePt":14,"bodyColor":"#222222","headingColor":"#ff6600","lineSpacing":432,"paragraphSpacingAfter":200,"marginTop":1440,"marginBottom":1440,"marginLeft":1440,"marginRight":1440,"quoteBg":"#fff7e6","previewColor":"#ff6600","description":"17px 正文 / 橙色强调 / 资讯感标题"}', 0, 0),
('tpl_xiaohongshu', '小红书图文模板', 'docx', '{"fontFamily":"PingFang SC","headingFontFamily":"PingFang SC","bodyFontSizePt":11,"headingFontSizePt":13,"bodyColor":"#333333","headingColor":"#ff2442","lineSpacing":444,"paragraphSpacingAfter":200,"marginTop":1440,"marginBottom":1440,"marginLeft":1440,"marginRight":1440,"quoteBg":"#fff0f3","previewColor":"#ff2442","description":"15px 正文 / 粉红标签 / 轻松活泼"}', 0, 0),
('tpl_baijiahao', '百家号模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":12,"headingFontSizePt":14,"bodyColor":"#262626","headingColor":"#1677ff","lineSpacing":432,"paragraphSpacingAfter":180,"marginTop":1440,"marginBottom":1440,"marginLeft":1440,"marginRight":1440,"quoteBg":"#e6f4ff","previewColor":"#1677ff","description":"16px 正文 / 蓝色层级 / 信息密度高"}', 0, 0),
('tpl_business', '简约商务模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":11,"headingFontSizePt":12,"bodyColor":"#262626","headingColor":"#1677ff","lineSpacing":420,"paragraphSpacingAfter":180,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#f0f5ff","previewColor":"#1677ff","description":"14px 正文 / 深蓝标题 / 清晰层级"}', 0, 0),
('tpl_marketing', '营销转化模板', 'docx', '{"fontFamily":"PingFang SC","headingFontFamily":"PingFang SC","bodyFontSizePt":14,"headingFontSizePt":15,"bodyColor":"#262626","headingColor":"#cf1322","lineSpacing":432,"paragraphSpacingAfter":220,"marginTop":1440,"marginBottom":1440,"marginLeft":1440,"marginRight":1440,"quoteBg":"#fff2f0","previewColor":"#cf1322","description":"18px 正文 / 红色强调 / 引导行动"}', 0, 0),
('tpl_story', '故事叙事模板', 'docx', '{"fontFamily":"Georgia","headingFontFamily":"Georgia","bodyFontSizePt":12,"headingFontSizePt":14,"bodyColor":"#262626","headingColor":"#5a3e2b","lineSpacing":444,"paragraphSpacingAfter":220,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#faf5ef","previewColor":"#8b5e34","description":"16px 正文 / 暖棕标题 / 沉浸阅读"}', 0, 0),
('tpl_academic', '学术报告模板', 'docx', '{"fontFamily":"宋体","headingFontFamily":"黑体","bodyFontSizePt":12,"headingFontSizePt":13,"bodyColor":"#262626","headingColor":"#1a1a1a","lineSpacing":360,"paragraphSpacingAfter":160,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#fafafa","previewColor":"#333333","description":"宋体 / 1.5 倍行距 / 自动编号"}', 0, 0),
('tpl_magazine', '杂志大字模板', 'docx', '{"fontFamily":"Georgia","headingFontFamily":"Georgia","bodyFontSizePt":12,"headingFontSizePt":14,"bodyColor":"#262626","headingColor":"#1a1a1a","lineSpacing":456,"paragraphSpacingAfter":240,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#fafafa","previewColor":"#1a1a1a","description":"大标题居中 / 衬线字体 / 留白呼吸"}', 0, 0),
('tpl_card', '卡片分块模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":11,"headingFontSizePt":13,"bodyColor":"#262626","headingColor":"#07c160","lineSpacing":432,"paragraphSpacingAfter":200,"marginTop":1440,"marginBottom":1440,"marginLeft":1440,"marginRight":1440,"quoteBg":"#ffffff","previewColor":"#07c160","description":"分块卡片 / 阴影层级 / 信息聚焦"}', 0, 0),
('tpl_checklist', '极简清单模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":11,"headingFontSizePt":13,"bodyColor":"#262626","headingColor":"#07c160","lineSpacing":432,"paragraphSpacingAfter":180,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#f6ffed","previewColor":"#07c160","description":"清单体 / 勾选符号 / 行动导向"}', 0, 0),
('tpl_dark', '深色沉浸模板', 'docx', '{"fontFamily":"微软雅黑","headingFontFamily":"微软雅黑","bodyFontSizePt":12,"headingFontSizePt":14,"bodyColor":"#f0f0f0","headingColor":"#95de64","lineSpacing":432,"paragraphSpacingAfter":200,"marginTop":1440,"marginBottom":1440,"marginLeft":1800,"marginRight":1800,"quoteBg":"#333333","previewColor":"#07c160","description":"深色背景 / 高对比 / 沉浸阅读"}', 0, 0);

-- Seed data: tracks
INSERT INTO tu_track (id, name, icon, sort_order, preview_bloggers, intro, platforms, cover_json) VALUES
('track001', '情感故事', '💬', 1, '', '深夜情感、婚姻观察、两性关系', '公众号、今日头条', '{"icon":"💬","gradient":"linear-gradient(135deg, #93c5fd 0%, #60a5fa 100%)"}'),
('track002', '科技数码', '📱', 2, '', '手机评测、数码开箱、科技趋势', '公众号、今日头条、百家号', '{"icon":"📱","gradient":"linear-gradient(135deg, #fdba74 0%, #fb923c 100%)"}'),
('track003', '职场成长', '🌱', 3, '', '职场经验、沟通技巧、升职加薪', '公众号', '{"icon":"🌱","gradient":"linear-gradient(135deg, #86efac 0%, #4ade80 100%)"}'),
('track004', '健康养生', '❤️', 4, '', '中医养生、健康饮食、运动健身', '今日头条、百家号', '{"icon":"❤️","gradient":"linear-gradient(135deg, #c4b5fd 0%, #a78bfa 100%)"}');

-- Seed data: bloggers
INSERT INTO tu_blogger (id, name, avatar, tagline, track_id, rank_num, link, platform) VALUES
('b001', '深夜情感电台', '👩', '128 篇爆款 · 10w+ 平均阅读', 'track001', 1, '', '公众号、今日头条'),
('b002', '老张说感情', '👨', '96 篇爆款 · 8w+ 平均阅读', 'track001', 2, '', '公众号'),
('b003', '小鹿乱撞', '👩', '84 篇爆款 · 12w+ 平均阅读', 'track001', 3, '', '今日头条、百家号'),
('b004', '婚姻观察室', '👨', '72 篇爆款 · 6w+ 平均阅读', 'track001', 4, '', '百家号'),
('b005', '数码研究所', '👨', '200 篇爆款 · 15w+ 平均阅读', 'track002', 1, '', '公众号、今日头条、百家号'),
('b006', '职场充电宝', '👩', '150 篇爆款 · 9w+ 平均阅读', 'track003', 1, '', '公众号'),
('b007', '养生堂', '👨', '110 篇爆款 · 7w+ 平均阅读', 'track004', 1, '', '今日头条、百家号');

-- Seed data: posts
INSERT INTO tu_post (id, title, url, blogger_id, content, platform, summary, tag, `reads`, `likes`, `comments`, status) VALUES
('p001', '人到中年，这三件事越早明白越好', '', 'b001',
 '<h2>人到中年，这三件事越早明白越好</h2><p>年轻时我们总以为人生有无限可能，可以肆意挥霍时间，可以不计后果地尝试。可真到了中年，经历了生活的起起伏伏，我们才渐渐懂得：有些道理，明白得越早就越能掌握人生的主动权。</p><p><strong>第一件事：健康是一切的基础。</strong></p><p>没有健康，所有的成就、财富、地位都不过是空中楼阁。中年以后，身体机能开始走下坡路，曾经熬夜通宵第二天依然生龙活虎的日子一去不复返。这个时候，规律作息、适度运动、定期体检，不是选择题，而是必答题。</p><p><strong>第二件事：关系需要经营。</strong></p><p>无论是亲情、友情还是爱情，再好的关系也经不起长期的忽视和消耗。中年人的社交圈往往会自动收缩，留下的都是真正值得珍惜的人。用心经营这些关系，才能在关键时刻有所依靠。</p><p><strong>第三件事：心态决定状态。</strong></p><p>中年危机并不可怕，可怕的是你对此毫无准备，或者被焦虑彻底吞噬。学会接纳自己的不完美，接纳生活的不如意，保持一颗平和而积极的心，才能在这个阶段活出新的精彩。</p><p>人生下半场，拼的不是谁跑得快，而是谁走得稳。愿你早日明白这三件事，把接下来的路走得更加从容。</p>',
 '公众号', '年轻时总以为人生有无限可能，到了中年才发现，真正决定你后半生质量的，不过是这三件事：健康、关系和心态...', '10w+', '12.5w', '3,240', '586', '已上架'),

('p002', '一个细节暴露了你和他的关系真假', '', 'b002',
 '<h2>一个细节暴露了你和他的关系真假</h2><p>真正爱你的人，会在细节上给你足够的安全感。他会记得你不爱吃香菜，会注意到你换了新发型，会在你生病时第一时间出现。</p><p>相反，如果一个人总是以忙为借口忽略你的消息，总是忘记你们约定好的事情，总是让你感到不安和患得患失，那这段关系的真假，其实已经不言而喻。</p><p>细节不会说谎，因为它来自下意识的本能反应。</p>',
 '公众号', '感情里最可怕的，不是争吵，而是你以为很亲密，其实对方早已心不在焉。真正爱你的人，会在这些细节上...', '10w+', '10.2w', '2,890', '412', '已上架'),

('p003', '为什么越懂事的人，越容易被辜负？', '', 'b003',
 '<h2>为什么越懂事的人，越容易被辜负？</h2><p>懂事的人总是习惯性地把别人的需求放在前面，却忘了自己也需要被照顾。</p><p>你总是害怕麻烦别人，所以一个人扛下所有；你总是体谅别人的难处，所以一次次委屈自己。久而久之，别人习惯了你的付出，却忘记了你也需要被珍惜。</p><p>懂事是修养，但不必懂事到失去自我。学会适度表达自己的需求，才是对自己最好的善待。</p>',
 '今日头条', '懂事是一种美德，但过度懂事往往意味着压抑自己的需求。当你总是把别人的感受放在第一位，久而久之...', '8w+', '8.6w', '1,560', '298', '已上架'),

('p004', '好的婚姻，不是忍出来的', '', 'b004',
 '<h2>好的婚姻，不是忍出来的</h2><p>婚姻需要经营，而不是一味地忍耐。很多人把婚姻的不幸归结为"不够包容"，于是一忍再忍，却不知道有些底线一旦退让，只会换来更多的得寸进尺。</p><p>真正幸福的婚姻，靠的是沟通、理解和共同成长。遇到问题要敢于表达，有分歧要学会协商，而不是把委屈咽进肚子里。</p><p>好的婚姻，是两个人一起把日子过好，而不是一个人苦苦支撑。</p>',
 '百家号', '很多人把婚姻的不幸归结为"不够包容"，于是一忍再忍。但真正的幸福婚姻，靠的是沟通、理解和共同成长...', '8w+', '7.9w', '1,820', '356', '已上架'),

('p005', '2026年最值得入手的5款手机', '', 'b005',
 '<h2>2026年最值得入手的5款手机</h2><p>经过长达三个月的深度评测，我们从性能、续航、影像、系统四个维度，为大家筛选出今年最值得购买的5款旗舰手机。</p><p>第一款主打影像，搭载一英寸大底传感器；第二款主打游戏，散热堆料十足；第三款主打商务，隐私保护到位；第四款主打性价比；第五款则是折叠屏新标杆。</p><p>如果你近期有换机计划，这篇文章一定要看完。</p>',
 '公众号', '从性能、续航、影像、系统四个维度，深度评测2026年最值得购买的5款旗舰手机。', '10w+', '15w', '5,200', '890', '已上架'),

('p006', '职场新人必看的沟通技巧', '', 'b006',
 '<h2>职场新人必看的沟通技巧</h2><p>很多职场新人工作能力不差，却因为不会沟通而屡屡碰壁。其实职场沟通并没有那么复杂，掌握以下几个原则就够了。</p><p>第一，结论先行。汇报工作先说结果，再说过程。第二，带着方案去提问。第三，及时同步进度，让领导有掌控感。第四，学会换位思考，用对方听得懂的语言表达。</p>',
 '公众号', '工作能力不差，却因为不会沟通而屡屡碰壁？掌握这几个原则，让你职场沟通游刃有余。', '8w+', '9w', '2,100', '340', '已上架'),

('p007', '春季养肝，多吃这3种食物', '', 'b007',
 '<h2>春季养肝，多吃这3种食物</h2><p>春天是养肝的最佳时节。中医认为，肝主疏泄，春季阳气升发，正是调养肝脏的好时机。</p><p>第一种是菠菜，富含铁和维生素，有助于疏肝养血。第二种是枸杞，滋补肝肾、明目润肺。第三种是山药，健脾益气、滋肾益精。这三种食材搭配食用，既美味又养生。</p>',
 '今日头条', '春天是养肝的最佳时节，多吃这三种食物，疏肝养血、滋补肝肾。', '5w+', '7w', '1,500', '220', '已上架');

CREATE TABLE tu_guide (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(50) DEFAULT '创作技巧',
    description VARCHAR(500) DEFAULT '',
    content LONGTEXT,
    link VARCHAR(1000) DEFAULT '',
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT '已上架',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Seed data: guides (help documentation)
INSERT INTO tu_guide (id, title, category, description, content, link, sort_order, status) VALUES
('g001', '公众号有思想、有创造性标题写作的10个黄金法则', '创作技巧', '标题是文章的第一张脸，决定了80%的打开率。掌握数字法则、悬念制造、痛点切入、蹭热点、对比反差、权威背书、情绪共鸣等技巧，让你的标题自带点击率。',
 '<h2>一、标题的重要性</h2><p>标题是文章的第一张脸，决定了80%的打开率。一个好标题能在3秒内抓住读者的注意力，而一个平庸的标题则会让精心打磨的内容石沉大海。</p><h2>二、数字法则</h2><p>人类大脑对数字特别敏感。使用具体数字的标题更容易获得点击，比如"7天涨粉1万的秘密"比"快速涨粉的方法"更有吸引力。</p><h2>三、悬念制造</h2><p>适度留白，激发好奇心。例如"90%的人都不知道的朋友圈运营技巧"，让读者产生"我是不是那90%"的疑问。</p><h2>四、痛点切入</h2><p>直击目标读者的痛点，如"公众号写了3年还是没人看？你可能犯了这5个错"。</p><h2>五、蹭热点技巧</h2><p>结合时事热点，但要注意与自身定位的相关性，不要为了蹭热点而偏离账号调性。</p><h2>六、对比反差</h2><p>利用反差制造冲突感，如"月薪3000和月薪30000的文案，差距就在这一个字"。</p><h2>七、权威背书</h2><p>借助名人、机构、数据提升可信度，如"腾讯产品经理都在用的用户增长模型"。</p><h2>八、情绪共鸣</h2><p>标题要能触发读者的情绪，无论是焦虑、愤怒、喜悦还是感动，情绪越强烈，传播性越强。</p><h2>九、避免标题党</h2><p>吸引眼球的同时不能欺骗读者，否则会被取关甚至举报，得不偿失。</p><h2>十、测试与优化</h2><p>同一篇文章准备2-3个标题，通过小范围测试选择打开率最高的版本。</p><p><strong>结语：</strong>标题写作是一门需要长期练习的手艺，多看、多写、多测，才能找到最适合自己账号风格的标题公式。</p>',
 '', 1, '已上架'),

('g002', '新号冷启动：前30天必须做到的7件事', '养号技巧', '很多创作者文章写得很好，但阅读量始终破不了500。问题往往出在账号权重上。本文从定位、内容、互动、数据四个维度，告诉你新号如何快速度过冷启动期。',
 '<h2>一、明确账号定位</h2><p>冷启动期的第一件事不是写内容，而是想清楚：你是谁？你要写给谁看？你能解决什么问题？定位越清晰，后续的内容创作和粉丝积累就越容易。</p><h2>二、保持更新频率</h2><p>新号阶段建议每周至少更新3-4篇，最好日更。持续更新不仅能让平台识别你的活跃度，也能快速积累内容资产，给新读者留下"这个号还在认真做"的印象。</p><h2>三、内容要有信息增量</h2><p>不要写谁都知道的常识，要提供独特的视角、深入的拆解或可落地的方案。信息增量是转发和收藏的核心驱动力。</p><h2>四、善用标签和关键词</h2><p>在标题、摘要和正文中合理布局关键词，提升在微信搜一搜中的搜索排名，获取精准的长尾流量。</p><h2>五、主动引导互动</h2><p>新号没有流量权重，更需要通过互动来提升算法推荐。每篇文章结尾都要设置一个与内容相关的问题，鼓励读者留言。</p><h2>六、朋友圈冷启动</h2><p>把文章发到朋友圈时，不要只丢一个链接，而要写一段吸引人的推荐语。让朋友帮忙转发时，也要提供现成的文案，降低他们的操作成本。</p><h2>七、数据分析与快速迭代</h2><p>每天看后台数据，关注打开率、分享率、完读率。发现某种类型的文章数据特别好，就要快速跟进，形成系列。</p><p><strong>结语：</strong>新号冷启动没有捷径，但只要有清晰的定位、稳定的输出和快速的迭代，30天内突破冷启动是完全可能的。</p>',
 '', 2, '已上架'),

('g003', 'AI辅助创作的正确姿势：人+AI效率翻倍', '创作技巧', '把AI当成助理而不是替代品。掌握提示词工程、结构化输入和人工润色这三步，你的创作效率至少提升3倍，同时保持内容的温度和个性。',
 '<h2>一、摆正心态：AI是助理，不是替身</h2><p>AI可以帮你收集资料、梳理结构、生成初稿，但最终的选题判断、情感表达和价值观输出，必须是人来完成的。完全依赖AI的内容，读者是能闻出"机器味"的。</p><h2>二、提示词工程：让AI听懂你的话</h2><p>好的提示词要包含五个要素：角色（你是一位资深情感博主）、背景（目标读者是25-35岁的职场女性）、任务（写一篇关于"职场边界感"的公众号文章）、要求（语气温暖、有故事、有金句）、格式（先给大纲，再逐段展开）。</p><h2>三、结构化输入：提高输出质量</h2><p>不要一次性让AI写完整篇文章。正确的流程是：先出选题和大纲，确认方向后再逐段生成，每生成一段就人工审核、修改，再进入下一段。这样既能保证质量，又能随时纠偏。</p><h2>四、人工润色：注入温度和个性</h2><p>AI生成的文字往往过于"正确"而缺乏个性。润色时要加入自己的口头禅、真实故事、独特观点和情感波动，让文章读起来像一个人在说话，而不是一台机器在播报。</p><h2>五、常见误区</h2><p>误区一：直接复制粘贴AI内容；误区二：让AI写自己不懂的领域；误区三：过度追求产量而忽视质量；误区四：把AI当成搜索替代品，不做事实核查。</p><p><strong>结语：</strong>未来属于会用AI的创作者，而不是被AI取代的创作者。掌握人+AI的协作模式，你的创作效率和内容质量都会迎来质的飞跃。</p>',
 '', 3, '已上架'),

('g004', '公众号内容结构的5种经典布局', '创作技巧', '清晰的结构是内容可读性的基础。本文介绍总分总、问题-方案、时间线、对比、清单体五种经典结构，帮你写出逻辑清晰、读完率高的文章。',
 '<h2>一、为什么需要结构</h2><p>清晰的结构是内容可读性的基础。结构混乱的文章会让读者迷失方向，即使观点再好也难以被接受。</p><h2>二、总分总结构</h2><p>最经典的结构：开篇点题，中间分点论述，结尾升华总结。适合干货类、观点类文章。优点是逻辑清晰，读者容易跟上思路。</p><h2>三、问题-方案结构</h2><p>先抛出读者面临的问题，再逐一给出解决方案。这是转化率最高的结构之一，适合产品种草、课程推广、工具推荐等类型。</p><h2>四、时间线结构</h2><p>按照时间顺序展开叙述，适合人物故事、历史回顾、成长记录、项目复盘等类型。优点是故事感强，容易带动情绪。</p><h2>五、对比结构</h2><p>通过正反对比、前后对比、A/B对比来突出核心观点，增强说服力。适合观点文、测评文、职场方法论。</p><h2>六、清单体结构</h2><p>用1、2、3、4的清单形式呈现内容，降低阅读门槛，方便读者收藏转发。适合干货盘点、资源汇总、避坑指南。</p><h2>七、排版配合技巧</h2><p>段落长度控制在手机屏3行以内，小标题要醒目，重点内容加粗或变色，长文每隔500字配一张图，给读者喘息的空间。</p><p><strong>结语：</strong>没有最好的结构，只有最适合的结构。根据内容类型和表达目的灵活选择，让文章既有深度又有可读性。</p>',
 '', 4, '已上架'),

('g005', '提升公众号读者互动率的8个技巧', '养号技巧', '互动率是衡量内容价值的重要指标，也直接影响推荐算法权重。本文分享文末提问、投票互动、抽奖激励、精选留言、制造争议点等实战技巧。',
 '<h2>一、互动率的重要性</h2><p>互动率（留言、点赞、在看、转发）不仅影响推荐算法的权重，更是衡量内容价值的重要指标。高互动意味着高粘性。</p><h2>二、文末提问法</h2><p>在文章结尾抛出一个与内容相关的问题，引导读者留言分享自己的看法或经历。问题要具体，不能太宽泛。</p><h2>三、投票互动法</h2><p>利用公众号的投票功能，让读者参与到话题讨论中来。投票结果还可以成为下次选题的素材，一举两得。</p><h2>四、抽奖激励法</h2><p>通过设置留言抽奖、转发抽奖等活动，短期内快速提升互动数据。注意奖品要与账号定位相关，吸引精准粉丝。</p><h2>五、精选留言置顶</h2><p>认真回复读者留言，并把优质留言置顶。让读者感受到被重视，这会激发更多人的留言欲望，形成正向循环。</p><h2>六、制造争议点</h2><p>适度抛出一些有争议的观点，引发读者讨论。但要注意把握好尺度，避免引发负面舆情或价值观冲突。</p><h2>七、情感共鸣引导</h2><p>当文章内容触动了读者的情感时，主动引导他们分享、点赞，把这种情绪转化为行动。情绪是传播的最大动力。</p><h2>八、建立读者社群</h2><p>把核心读者导入微信群，在日常运营中培养感情。这些人会成为你内容最忠实的传播者，也是你测试选题的最佳样本。</p><p><strong>结语：</strong>互动是双向的，你付出多少真诚，读者就会回报多少热情。把每一次互动都当成建立关系的机会。</p>',
 '', 5, '已上架'),

('g006', '公众号排版美化的实用指南', '创作技巧', '好的排版能让阅读体验提升一个档次。本文从字号行距、颜色搭配、段落留白、配图选择、重点标注、排版工具等方面，给出可直接落地的排版标准。',
 '<h2>一、排版的价值</h2><p>好的排版能让阅读体验提升一个档次，让读者更愿意读完、收藏和转发。排版是内容的"第二张脸"。</p><h2>二、字号与行距</h2><p>正文字号建议14-16px，标题18-20px，行距1.5-1.75倍，字间距1-2px。这是最适合手机阅读的标准配置。</p><h2>三、颜色搭配原则</h2><p>全文颜色不要超过3种。正文用深灰色（#333333或#595959）比纯黑色更柔和，重点内容可以用品牌色点缀，但不要大面积使用亮色。</p><h2>四、段落与留白</h2><p>每段之间空一行，长文适当插入小标题或配图，给读者喘息的空间。手机屏幕阅读时代，留白比密集文字更受欢迎。</p><h2>五、配图选择技巧</h2><p>封面图决定打开率，文中配图决定读完率。图片要高清、相关、风格统一。尽量使用自己拍摄或设计的图，避免版权风险。</p><h2>六、引用框与重点标注</h2><p>对核心观点、金句、数据可以用引用框、色块背景等方式突出，方便读者快速扫读。标注的比例控制在全文的10%以内。</p><h2>七、排版工具推荐</h2><p>秀米、135编辑器、壹伴助手、Markdown Here等都是常用的公众号排版工具。建议建立几套固定模板，提高效率。</p><h2>八、移动端预览检查</h2><p>排版完成后一定要在手机上预览，电脑上的效果和手机上往往差别很大。特别是图片显示、段落长度、按钮位置要重点检查。</p><p><strong>结语：</strong>排版不是越花哨越好，而是要服务于阅读。简洁、清晰、有重点的排版，才是最长久的风格。</p>',
 '', 6, '已上架'),

('g007', '公众号最佳推送时间的选择策略', '养号技巧', '推送时间直接决定文章的初始阅读量。本文分析黄金时段、不同人群差异、固定推送好处、周末差异，帮你找到最适合自己账号的推送节奏。',
 '<h2>一、推送时间的影响</h2><p>推送时间直接决定了文章的初始阅读量，而初始阅读数据又会影响后续的推荐分发。选错时间，再好的内容也会被埋没。</p><h2>二、黄金时段分析</h2><p>公众号的黄金阅读时段通常是：早上7-9点（通勤时间）、中午12-13点（午休时间）、晚上21-23点（睡前时间）。</p><h2>三、不同人群的时间差异</h2><p>面向上班族的内容适合早晚通勤时段；面向学生群体的内容适合午休和晚上；面向宝妈的内容适合上午10点和下午3点。</p><h2>四、固定推送的好处</h2><p>长期固定时间推送能培养读者的阅读习惯。比如每天晚上10点推送，读者会养成睡前看你的文章的习惯，打开率会更稳定。</p><h2>五、避开高峰竞争</h2><p>黄金时段虽然流量大，但竞争也激烈。对于小号来说，选择一个次高峰时段（如下午3点或晚上11点）反而更容易脱颖而出。</p><h2>六、热点内容的特殊处理</h2><p>热点内容讲究时效性，越快推送越好，哪怕不是黄金时段也要抢发。热点过了12小时，价值就会大打折扣。</p><h2>七、周末与工作日的差异</h2><p>周末上午10-11点和晚上20-22点是不错的时段，但深度干货类内容在周末的表现往往不如工作日，轻娱乐类内容则相反。</p><h2>八、AB测试找最佳时间</h2><p>同样的内容在不同时间推送，观察数据表现。通过至少4周的测试，找到最适合自己账号的推送时间。</p><p><strong>结语：</strong>推送时间是一个需要长期测试和优化的变量，找到适合自己账号的节奏，并坚持下去。</p>',
 '', 7, '已上架'),

('g008', '公众号涨粉引流的15个实战方法', '养号技巧', '涨粉是所有公众号运营者的永恒话题。本文从内容涨粉、互推、社群裂变、平台引流、活动涨粉、付费投放等15个维度，给出可落地的涨粉方案。',
 '<h2>一、内容涨粉是根本</h2><p>所有涨粉技巧中，优质内容是最核心、最持久、成本最低的涨粉方式。一篇10万+的爆款文章可能带来几千甚至几万粉丝。</p><h2>二、互推涨粉</h2><p>与同量级、同领域的账号互相推荐，精准且高效。要注意账号调性的匹配，互推太频繁会伤害读者体验。</p><h2>三、社群裂变</h2><p>通过设计有吸引力的海报和诱饵（如资料包、课程），利用读者的人际关系链实现粉丝裂变增长。</p><h2>四、平台引流</h2><p>在知乎、小红书、抖音、B站等平台分发内容，把公域流量引导到公众号私域。每个平台的内容形式要适配平台调性。</p><h2>五、活动涨粉</h2><p>通过线上线下活动吸引目标用户关注，如直播、沙龙、打卡营、资料包领取等。活动要有明确的转化路径。</p><h2>六、付费投放</h2><p>对于有一定预算的账号，可以通过腾讯广告、朋友圈广告、KOL投放等方式快速获取精准粉丝。</p><h2>七、SEO优化</h2><p>文章标题和摘要中布局关键词，提高在微信搜一搜中的排名，获取长尾搜索流量。</p><h2>八、视频号联动</h2><p>利用微信视频号的流量红利，在视频号主页和视频中引导关注公众号，这是目前微信生态内最精准的引流方式之一。</p><h2>九、老带新激励</h2><p>设置推荐奖励机制，鼓励老粉丝邀请新朋友关注。奖励可以是资料包、课程优惠券或实物礼品。</p><h2>十、投稿涨粉</h2><p>向头部公众号投稿，在作者简介处留下自己的公众号信息。一旦被大号转载，涨粉效果非常明显。</p><h2>十一、资料包引流</h2><p>整理行业资料包、模板、工具清单等，作为关注诱饵进行引流。资料要真正有实用价值，不能敷衍。</p><h2>十二、朋友圈运营</h2><p>打造有传播性的朋友圈内容，让粉丝主动帮你转发和推荐。朋友圈是熟人社交，信任转化率最高。</p><h2>十三、菜单栏与自动回复</h2><p>设计好关注后的自动回复和底部菜单，给新粉丝良好的第一印象，提升留存率。</p><h2>十四、UGC内容征集</h2><p>定期向读者征集故事、照片、经验，让读者从内容的消费者变成生产者，增强归属感。</p><h2>十五、粉丝质量大于数量</h2><p>1000个精准粉丝的价值可能超过10万个泛粉丝。不要为了涨粉而涨粉，核心目标是找到真正需要你的人。</p><p><strong>结语：</strong>涨粉是一场持久战，没有一招制胜的秘诀。多管齐下、持续优化，才能稳步积累高质量粉丝。</p>',
 '', 8, '已上架');

CREATE TABLE tu_help (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(50) DEFAULT '常见问题',
    content LONGTEXT,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT '已上架',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO tu_help (id, title, category, content, sort_order, status) VALUES
('h001', '如何快速找到适合我的赛道', '快速入门', '<h2>如何快速找到适合我的赛道</h2><p>在首页浏览热门赛道，查看各赛道的头部博主数量和爆款文章数据。点击感兴趣的赛道进入详情页，了解更多信息。</p>', 1, '已上架'),
('h002', '账号到期后如何续费', '会员说明', '<h2>账号到期后如何续费</h2><p>账号到期后，请联系管理员进行续费操作。续费成功后，系统会自动恢复您的使用权限。</p>', 2, '已上架'),
('h003', '修改密码操作指南', '账号问题', '<h2>修改密码操作指南</h2><p>进入个人中心，点击「密码设置」，输入旧密码和新密码后保存即可。</p>', 3, '已上架'),
('h004', '从零到一：AI 辅助创作完整流程', '创作流程', '<h2>从零到一：AI 辅助创作完整流程</h2><p>整个创作流程分为：选择赛道、查看博主与爆款、AI 辅助创作、导出 Word 四个步骤。</p>', 4, '已上架'),
('h005', '为什么导出的 Word 格式会错乱', '常见问题', '<h2>为什么导出的 Word 格式会错乱</h2><p>请确保使用最新版本的 Word 打开导出的文件。如果仍有问题，请联系客服。</p>', 5, '已下架');

CREATE TABLE tu_daily_recommend (
    id VARCHAR(64) PRIMARY KEY,
    track_id VARCHAR(64) NOT NULL COMMENT '赛道ID',
    platform VARCHAR(50) NOT NULL COMMENT '平台，如：公众号/今日头条/百家号',
    title VARCHAR(255) NOT NULL COMMENT '推荐主题/标题',
    summary VARCHAR(500) DEFAULT '' COMMENT '推荐摘要/描述',
    ref_post_id VARCHAR(64) DEFAULT '' COMMENT '关联参考文章ID',
    ref_url VARCHAR(1000) DEFAULT '' COMMENT '参考文章URL',
    sort_order INT DEFAULT 0 COMMENT '排序，越小越靠前',
    status VARCHAR(20) DEFAULT '已上架' COMMENT '已上架/已下架',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_track_platform (track_id, platform)
);

CREATE TABLE tu_reference_post (
    id VARCHAR(64) PRIMARY KEY,
    track_id VARCHAR(64) NOT NULL COMMENT '赛道ID',
    platform VARCHAR(50) NOT NULL COMMENT '平台，如：公众号/今日头条/百家号',
    title VARCHAR(255) NOT NULL COMMENT '文章标题',
    content LONGTEXT COMMENT '文章内容（非公众号平台使用）',
    url VARCHAR(1000) DEFAULT '' COMMENT '原文链接（公众号平台使用）',
    sort_order INT DEFAULT 0 COMMENT '排序，越小越靠前',
    status VARCHAR(20) DEFAULT '已上架' COMMENT '已上架/已下架',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_track_platform (track_id, platform)
);

CREATE TABLE tu_subscription_post (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '目标用户ID',
    track_id VARCHAR(64) NOT NULL COMMENT '赛道ID',
    title VARCHAR(255) NOT NULL COMMENT '文章标题',
    description VARCHAR(1000) DEFAULT '' COMMENT '文章描述',
    file_url VARCHAR(1000) DEFAULT '' COMMENT '文件URL或链接',
    file_name VARCHAR(255) DEFAULT '' COMMENT '文件名',
    status VARCHAR(20) DEFAULT '已上架' COMMENT '已上架/已下架',
    used TINYINT DEFAULT 0 COMMENT '0=未使用, 1=已使用',
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_track (user_id, track_id),
    INDEX idx_created_at (created_at)
);

CREATE TABLE tu_help_category (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(20) DEFAULT 'blue',
    sort_order INT DEFAULT 0,
    is_deleted TINYINT DEFAULT 0 COMMENT '0=正常, 1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO tu_help_category (id, name, color, sort_order) VALUES
('hc001', '快速入门', 'blue', 1),
('hc002', '会员说明', 'orange', 2),
('hc003', '账号问题', 'purple', 3),
('hc004', '创作流程', 'cyan', 4),
('hc005', '常见问题', 'green', 5);

CREATE TABLE tu_membership_plan (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '套餐名称',
    price DECIMAL(10,2) NOT NULL COMMENT '现价',
    original_price DECIMAL(10,2) DEFAULT 0 COMMENT '原价',
    features_json LONGTEXT COMMENT '权益列表JSON数组',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT DEFAULT 1 COMMENT '1=启用, 0=禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO tu_membership_plan (id, name, price, original_price, features_json, sort_order, is_active) VALUES
('mp001', '基础版', 19.90, 39.90, '["每日推送3篇爆款文章","支持1个赛道订阅","基础AI改写","邮件客服支持"]', 1, 1),
('mp002', '标准版', 29.90, 59.90, '["每日推送5篇爆款文章","支持3个赛道订阅","高级AI改写","优先客服支持","数据报表"]', 2, 1),
('mp003', '专业版', 49.90, 99.90, '["每日推送10篇爆款文章","支持8个赛道订阅","深度AI改写","1对1专属顾问","数据报表","导出Word"]', 3, 1),
('mp004', '旗舰版', 99.90, 199.90, '["每日推送20篇爆款文章","支持全部赛道订阅","顶级AI改写","1对1专属顾问","数据报表","导出Word","API接口","白标定制"]', 4, 1);

-- Schema version tracking for incremental migrations
CREATE TABLE IF NOT EXISTS _schema_version (
    version VARCHAR(20) PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_by VARCHAR(50) DEFAULT ''
);
