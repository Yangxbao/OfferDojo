const fs = require('fs');
const dir = __dirname + '/backend/src/main/resources/sql/';

// H2 版本（无反引号、BOOLEAN、无 ON UPDATE）
const h2 = `

CREATE TABLE IF NOT EXISTS learning_resource (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    code               VARCHAR(64)  NOT NULL,
    track              VARCHAR(32)  NOT NULL,
    title              VARCHAR(300) NOT NULL,
    description        VARCHAR(1000) NOT NULL,
    url                VARCHAR(500) NOT NULL,
    source_type        VARCHAR(32)  NOT NULL,
    audience           VARCHAR(300),
    prerequisite       TEXT,
    version_note       VARCHAR(300),
    license_spdx       VARCHAR(64),
    license_name       VARCHAR(200),
    license_path       VARCHAR(300),
    license_status     VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    verified_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    verified_at        DATETIME,
    verification_evidence TEXT,
    allow_copy         BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_redistribute BOOLEAN      NOT NULL DEFAULT FALSE,
    attribution_required BOOLEAN    NOT NULL DEFAULT TRUE,
    share_alike        BOOLEAN      NOT NULL DEFAULT FALSE,
    rights_basis       VARCHAR(500),
    admin_note         VARCHAR(500),
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_lr_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_lr_track ON learning_resource (track);
CREATE INDEX IF NOT EXISTS idx_lr_status ON learning_resource (verified_status);

CREATE TABLE IF NOT EXISTS learning_node (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(64)  NOT NULL,
    track         VARCHAR(32)  NOT NULL,
    stage_order   INT          NOT NULL,
    node_order    INT          NOT NULL DEFAULT 0,
    title         VARCHAR(300) NOT NULL,
    goal          VARCHAR(1000) NOT NULL,
    knowledge     TEXT         NOT NULL,
    resource_codes TEXT        NOT NULL,
    task_codes    TEXT         NOT NULL,
    prereq_codes  TEXT,
    milestone     VARCHAR(500),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ln_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_ln_track ON learning_node (track, stage_order);

CREATE TABLE IF NOT EXISTS learning_plan (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    item_type     VARCHAR(16) NOT NULL,
    item_code     VARCHAR(64) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'TODO',
    note          VARCHAR(1000),
    started_at    DATETIME,
    completed_at  DATETIME,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_lp_user_item UNIQUE (user_id, item_type, item_code),
    CONSTRAINT uk_lp_user_item_code UNIQUE (user_id, item_code)
);
CREATE INDEX IF NOT EXISTS idx_lp_user ON learning_plan (user_id);
`;

const mysql = `

CREATE TABLE IF NOT EXISTS \`learning_resource\` (
    \`id\`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    \`code\`               VARCHAR(64)  NOT NULL COMMENT '资源编码，如 RES-JAVA-001',
    \`track\`              VARCHAR(32)  NOT NULL COMMENT '技术方向：java-fullstack / ai-fullstack / python-agent',
    \`title\`              VARCHAR(300) NOT NULL COMMENT '资源标题',
    \`description\`        VARCHAR(1000) NOT NULL COMMENT '简介（事实性描述，不复制原文）',
    \`url\`                VARCHAR(500) NOT NULL COMMENT '原始链接',
    \`source_type\`        VARCHAR(32)  NOT NULL COMMENT '类型：ROADMAP / NOTES / EXAMPLES / DOCS / COURSE',
    \`audience\`           VARCHAR(300) NULL COMMENT '适用人群',
    \`prerequisite\`       TEXT         NULL COMMENT '前置知识，JSON 字符串数组',
    \`version_note\`       VARCHAR(300) NULL COMMENT '依赖/版本提示（不适用时写：不适用）',
    \`license_spdx\`       VARCHAR(64)  NULL COMMENT 'SPDX 标识；未知为 NULL',
    \`license_name\`       VARCHAR(200) NULL COMMENT '许可证名称或文件说明',
    \`license_path\`       VARCHAR(300) NULL COMMENT '许可证文件路径（仓库内）',
    \`license_status\`     VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN / VERIFIED / NOASSERTION',
    \`verified_status\`    VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / VERIFIED / PUBLISHED / OFFLINE',
    \`verified_at\`        DATETIME     NULL COMMENT '最后核验时间',
    \`verification_evidence\` TEXT      NULL COMMENT '核验证据 JSON：默认分支/最近提交/核验项/未核实字段',
    \`allow_copy\`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '允许复制（未核验默认禁止）',
    \`allow_redistribute\` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '允许再分发（未核验默认禁止）',
    \`attribution_required\` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '要求署名',
    \`share_alike\`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '要求相同方式共享',
    \`rights_basis\`       VARCHAR(500) NULL COMMENT '权限判定依据与证据链接',
    \`admin_note\`         VARCHAR(500) NULL COMMENT '管理员备注',
    \`created_at\`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    \`updated_at\`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (\`id\`),
    UNIQUE KEY \`uk_lr_code\` (\`code\`),
    KEY \`idx_lr_track\` (\`track\`),
    KEY \`idx_lr_status\` (\`verified_status\`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='开源学习资源元数据';

CREATE TABLE IF NOT EXISTS \`learning_node\` (
    \`id\`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    \`code\`          VARCHAR(64)  NOT NULL COMMENT '节点编码，如 NJ-J101',
    \`track\`         VARCHAR(32)  NOT NULL COMMENT '技术方向（同上）',
    \`stage_order\`   INT          NOT NULL COMMENT '阶段序号（1 开始）',
    \`node_order\`    INT          NOT NULL DEFAULT 0 COMMENT '阶段内节点序号',
    \`title\`         VARCHAR(300) NOT NULL COMMENT '节点标题',
    \`goal\`          VARCHAR(1000) NOT NULL COMMENT '学习目标',
    \`knowledge\`     TEXT         NOT NULL COMMENT '知识点，JSON 字符串数组',
    \`resource_codes\` TEXT        NOT NULL COMMENT '关联资源编码，JSON 字符串数组',
    \`task_codes\`     TEXT         NOT NULL COMMENT '关联实战任务编码，JSON 字符串数组',
    \`prereq_codes\`   TEXT         NULL COMMENT '前置节点编码，JSON 字符串数组',
    \`milestone\`      VARCHAR(500) NULL COMMENT '完成条件/里程碑',
    \`created_at\`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    \`updated_at\`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (\`id\`),
    UNIQUE KEY \`uk_ln_code\` (\`code\`),
    KEY \`idx_ln_track\` (\`track\`, \`stage_order\`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='学习路线节点';

CREATE TABLE IF NOT EXISTS \`learning_plan\` (
    \`id\`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    \`user_id\`       BIGINT      NOT NULL COMMENT '用户 id',
    \`item_type\`     VARCHAR(16) NOT NULL COMMENT '条目类型：RESOURCE / NODE',
    \`item_code\`     VARCHAR(64) NOT NULL COMMENT '资源或节点编码',
    \`status\`        VARCHAR(20) NOT NULL DEFAULT 'TODO' COMMENT 'TODO / IN_PROGRESS / DONE',
    \`note\`          VARCHAR(1000) NULL COMMENT '学习心得',
    \`started_at\`    DATETIME    NULL COMMENT '开始时间',
    \`completed_at\`  DATETIME    NULL COMMENT '完成时间',
    \`created_at\`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    \`updated_at\`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (\`id\`),
    UNIQUE KEY \`uk_lp_user_item\` (\`user_id\`, \`item_type\`, \`item_code\`),
    KEY \`idx_lp_user\` (\`user_id\`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='个人学习计划与记录';
`;

// H2：追加
let h = fs.readFileSync(dir + 'schema-h2.sql', 'utf8');
if (h.includes('learning_resource')) {
  console.error('H2 已包含 learning 表，中止'); process.exit(1);
}
fs.writeFileSync(dir + 'schema-h2.sql', h + h2);

// MySQL：追加
let m = fs.readFileSync(dir + 'schema-mysql.sql', 'utf8');
if (m.includes('learning_resource')) {
  console.error('MySQL 已包含 learning 表，中止'); process.exit(1);
}
fs.writeFileSync(dir + 'schema-mysql.sql', m + mysql);

console.log('两套 schema 干净追加完成');
console.log('h2 校验:', h.includes('learning_resource') === false, '→ 追加后包含:', fs.readFileSync(dir + 'schema-h2.sql', 'utf8').includes('learning_resource'));
console.log('mysql 校验:', fs.readFileSync(dir + 'schema-mysql.sql', 'utf8').includes('learning_resource'));
