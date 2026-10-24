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
