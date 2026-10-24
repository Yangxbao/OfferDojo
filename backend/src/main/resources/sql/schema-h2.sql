-- ============================================================
-- JobSim AI 数据库结构（H2，MySQL 兼容模式）
-- 供 demo profile（零依赖演示）与自动化测试使用，结构/schema-mysql.sql 保持一致。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(64)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    category          VARCHAR(32)  NOT NULL,
    difficulty        VARCHAR(16)  NOT NULL,
    tags              VARCHAR(255),
    summary           VARCHAR(500) NOT NULL,
    estimated_minutes INT          NOT NULL DEFAULT 30,
    background        TEXT         NOT NULL,
    requirements      TEXT         NOT NULL,
    constraint_items  TEXT         NOT NULL,
    deliverables      TEXT         NOT NULL,
    acceptance        TEXT         NOT NULL,
    rubric_json       TEXT         NOT NULL,
    mock_hints_json   TEXT,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_task_category ON task (category);
CREATE INDEX IF NOT EXISTS idx_task_status ON task (status);

CREATE TABLE IF NOT EXISTS task_submission (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    task_id    BIGINT      NOT NULL,
    content    TEXT        NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sub_user_task ON task_submission (user_id, task_id);
CREATE INDEX IF NOT EXISTS idx_sub_user_status ON task_submission (user_id, status);
CREATE INDEX IF NOT EXISTS idx_sub_task ON task_submission (task_id);

CREATE TABLE IF NOT EXISTS task_evaluation (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    submission_id    BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    task_id          BIGINT        NOT NULL,
    total_score      DECIMAL(4, 1) NOT NULL,
    dimension_scores TEXT          NOT NULL,
    strengths        TEXT          NOT NULL,
    problems         TEXT          NOT NULL,
    edge_cases       TEXT          NOT NULL,
    suggestions      TEXT          NOT NULL,
    follow_ups       TEXT          NOT NULL,
    next_task_codes  TEXT          NOT NULL,
    provider         VARCHAR(20)   NOT NULL,
    model            VARCHAR(100),
    notice           VARCHAR(500),
    prompt_version   VARCHAR(20),
    raw_response     TEXT,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_eval_submission UNIQUE (submission_id)
);
CREATE INDEX IF NOT EXISTS idx_eval_user_created ON task_evaluation (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_eval_task ON task_evaluation (task_id);

CREATE TABLE IF NOT EXISTS ai_call_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    provider    VARCHAR(20)  NOT NULL,
    model       VARCHAR(100),
    task_code   VARCHAR(64),
    success     BOOLEAN      NOT NULL DEFAULT FALSE,
    error_type  VARCHAR(50),
    message     VARCHAR(500),
    duration_ms INT,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_log_success ON ai_call_log (success);
CREATE INDEX IF NOT EXISTS idx_log_created ON ai_call_log (created_at);

CREATE TABLE IF NOT EXISTS interview_question (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(64)  NOT NULL,
    category   VARCHAR(32)  NOT NULL,
    title      VARCHAR(300) NOT NULL,
    frequency  VARCHAR(10)  NOT NULL DEFAULT 'MID',
    difficulty VARCHAR(16)  NOT NULL DEFAULT 'EASY',
    tags       VARCHAR(255),
    one_liner  VARCHAR(500) NOT NULL,
    plain      TEXT         NOT NULL,
    points     TEXT         NOT NULL,
    example    TEXT,
    follow_ups TEXT,
    version_note VARCHAR(200),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_bagu_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_bagu_category ON interview_question (category);
CREATE INDEX IF NOT EXISTS idx_bagu_freq ON interview_question (frequency);


CREATE TABLE IF NOT EXISTS followup_dialog (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    evaluation_id BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    question      VARCHAR(500) NOT NULL,
    answer        TEXT         NOT NULL,
    score         DECIMAL(4,1) NOT NULL,
    feedback      TEXT         NOT NULL,
    provider      VARCHAR(20)  NOT NULL DEFAULT 'MOCK',
    notice        VARCHAR(300),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_fd_eval ON followup_dialog (evaluation_id);


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
    category_tags     VARCHAR(255),
    language          VARCHAR(32),
    difficulty_level  VARCHAR(16),
    stars_count       BIGINT,
    forks_count       BIGINT,
    github_created_at DATETIME,
    github_updated_at DATETIME,
    github_pushed_at  DATETIME,
    quality_score     INT,
    quality_level     VARCHAR(20),
    featured          BOOLEAN,
    review_note       VARCHAR(500),
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

CREATE TABLE IF NOT EXISTS user_project (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT        NOT NULL COMMENT '投稿用户',
    name              VARCHAR(100)  NOT NULL COMMENT '项目名称',
    subtitle          VARCHAR(200)  NULL COMMENT '副标题',
    summary           VARCHAR(1000) NOT NULL COMMENT '项目简介',
    problem_solved    VARCHAR(1000) NULL COMMENT '解决的实际问题',
    tech_stack        VARCHAR(500)  NOT NULL COMMENT '主要技术栈，逗号分隔',
    category          VARCHAR(32)   NOT NULL COMMENT '项目分类',
    highlights        VARCHAR(2000) NULL COMMENT '亮点与核心功能',
    my_role           VARCHAR(1000) NULL COMMENT '自己负责的部分',
    challenges        VARCHAR(1000) NULL COMMENT '开发中解决的困难',
    learnings         VARCHAR(1000) NULL COMMENT '个人收获',
    repo_url          VARCHAR(500)  NULL COMMENT 'GitHub/Gitee 仓库地址',
    demo_url          VARCHAR(500)  NULL COMMENT '在线演示地址',
    cover_url         VARCHAR(500)  NULL COMMENT '封面图片地址（受限上传或外链）',
    readme_md         TEXT    NULL COMMENT '项目说明 Markdown（服务端存储，渲染时防 XSS）',
    ai_assisted       BOOLEAN    NOT NULL DEFAULT 0 COMMENT '是否 AI 辅助开发',
    ai_usage_note     VARCHAR(500)  NULL COMMENT 'AI 辅助使用范围说明',
    gh_stars          BIGINT        NULL COMMENT '仓库 Star（服务端经 GitHub API 获取，非用户填写）',
    gh_forks          BIGINT        NULL COMMENT '仓库 Fork 数',
    gh_language       VARCHAR(64)   NULL COMMENT '主要编程语言',
    gh_fetched_at     DATETIME      NULL COMMENT 'GitHub 元数据获取时间',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED/WITHDRAWN',
    review_comment    VARCHAR(1000) NULL COMMENT '审核意见',
    reviewed_at       DATETIME      NULL COMMENT '审核时间',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_up_user (user_id, status),
    KEY idx_up_status (status),
    KEY idx_up_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用户项目展示';

CREATE TABLE IF NOT EXISTS bagu_submission (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT        NOT NULL COMMENT '投稿用户',
    category          VARCHAR(32)   NOT NULL COMMENT '题目分类（与官方题库同套类目）',
    question_type     VARCHAR(20)   NOT NULL DEFAULT 'BAGU' COMMENT 'BAGU八股/INTERVIEW复盘/PROJECT项目实战/SYSTEM_DESIGN/DEBUG排查',
    difficulty        VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY/MEDIUM/HARD',
    title             VARCHAR(300)  NOT NULL COMMENT '题目标题',
    content           TEXT          NOT NULL COMMENT '题目内容（Markdown）',
    my_answer         TEXT          NULL COMMENT '自己的答案/思路',
    reference_answer  TEXT          NULL COMMENT '参考答案',
    example           TEXT          NULL COMMENT '代码/业务示例',
    tags              VARCHAR(255)  NULL COMMENT '标签，逗号分隔',
    company_info      VARCHAR(200)  NULL COMMENT '公司/岗位（自愿填写，需匿名化）',
    reference_url     VARCHAR(500)  NULL COMMENT '参考链接',
    anonymous         BOOLEAN    NOT NULL DEFAULT 0 COMMENT '是否匿名展示',
    origin_type       VARCHAR(20)   NOT NULL DEFAULT 'USER_ORIGINAL' COMMENT '来源：USER_ORIGINAL/USER_REVIEWED（用户复盘整理）',
    license_grant     VARCHAR(20)   NULL COMMENT '投稿授权：REVIEW_ONLY仅审核/PUBLIC_DISPLAY公开展示',
    license_granted_at DATETIME     NULL COMMENT '授权时间',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED/WITHDRAWN',
    review_comment    VARCHAR(1000) NULL COMMENT '审核意见',
    reviewed_at       DATETIME      NULL COMMENT '审核时间',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bs_user (user_id, status),
    KEY idx_bs_status (status),
    KEY idx_bs_category (category, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='社区面试题投稿';
