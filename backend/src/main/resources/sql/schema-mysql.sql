-- ============================================================
-- JobSim AI 数据库结构（MySQL 8）
-- 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- 演示数据（演示账号 + 12 个内置任务）由后端 DemoDataInitializer
-- 在表为空时自动写入，见 backend/src/main/java/com/jobsim/bootstrap/。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希（绝不存明文）',
    `nickname`      VARCHAR(50)  NULL COMMENT '昵称',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';

CREATE TABLE IF NOT EXISTS `task` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`             VARCHAR(64)  NOT NULL COMMENT '任务编码，如 API-001',
    `title`            VARCHAR(200) NOT NULL COMMENT '任务标题',
    `category`         VARCHAR(32)  NOT NULL COMMENT '类目编码：api-design / db-design / troubleshooting / testing / refactoring',
    `difficulty`       VARCHAR(16)  NOT NULL COMMENT '难度：EASY / MEDIUM / HARD',
    `tags`             VARCHAR(255) NULL COMMENT '标签，逗号分隔',
    `summary`          VARCHAR(500) NOT NULL COMMENT '一句话简介',
    `estimated_minutes` INT         NOT NULL DEFAULT 30 COMMENT '预计用时（分钟）',
    `background`       TEXT         NOT NULL COMMENT '任务背景（业务场景描述）',
    `requirements`     TEXT         NOT NULL COMMENT '业务需求，JSON 字符串数组',
    `constraint_items` TEXT         NOT NULL COMMENT '约束条件，JSON 字符串数组（避免使用保留字 constraints）',
    `deliverables`     TEXT         NOT NULL COMMENT '预期交付物，JSON 字符串数组',
    `acceptance`       TEXT         NOT NULL COMMENT '验收标准，JSON 字符串数组',
    `rubric_json`      TEXT         NOT NULL COMMENT '评分标准：维度/权重/评分指引，JSON 数组',
    `mock_hints_json`  TEXT         NULL COMMENT '模拟评估的关键词提示（MockAIProvider 用）；管理员手工新建的任务可为空，走通用评分',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT / PUBLISHED / OFFLINE',
    `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_code` (`code`),
    KEY `idx_task_category` (`category`),
    KEY `idx_task_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='模拟任务表';

CREATE TABLE IF NOT EXISTS `task_submission` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '提交用户 id',
    `task_id`    BIGINT      NOT NULL COMMENT '任务 id',
    `content`    MEDIUMTEXT  NOT NULL COMMENT '答案内容（Markdown 文本）',
    `status`     VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / EVALUATED 已提交并完成评估',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sub_user_task` (`user_id`, `task_id`),
    KEY `idx_sub_user_status` (`user_id`, `status`),
    KEY `idx_sub_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='任务提交（含草稿）表';

CREATE TABLE IF NOT EXISTS `task_evaluation` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `submission_id`      BIGINT        NOT NULL COMMENT '对应的提交 id（一次提交一份评估）',
    `user_id`            BIGINT        NOT NULL COMMENT '冗余用户 id，便于按人统计',
    `task_id`            BIGINT        NOT NULL COMMENT '冗余任务 id，便于按任务统计',
    `total_score`        DECIMAL(4, 1) NOT NULL COMMENT '加权总分（0.0 - 10.0）',
    `dimension_scores`   TEXT          NOT NULL COMMENT '各维度得分与评语，JSON 数组',
    `strengths`          TEXT          NOT NULL COMMENT '答案中的正确做法，JSON 字符串数组',
    `problems`           TEXT          NOT NULL COMMENT '存在的问题，JSON 字符串数组',
    `edge_cases`         TEXT          NOT NULL COMMENT '可能遗漏的边界情况，JSON 字符串数组',
    `suggestions`        TEXT          NOT NULL COMMENT '改进建议，JSON 字符串数组',
    `follow_ups`         TEXT          NOT NULL COMMENT '进一步的技术追问，JSON 字符串数组',
    `next_task_codes`    TEXT          NOT NULL COMMENT '建议下一步练习的任务编码，JSON 字符串数组',
    `provider`           VARCHAR(20)   NOT NULL COMMENT '评估来源：MOCK / REMOTE / MOCK_FALLBACK',
    `model`              VARCHAR(100)  NULL COMMENT '远程模型名（REMOTE 时记录）',
    `notice`             VARCHAR(500)  NULL COMMENT '提示信息，例如降级说明',
    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eval_submission` (`submission_id`),
    KEY `idx_eval_user_created` (`user_id`, `created_at`),
    KEY `idx_eval_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='AI 评估结果表';

CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider`    VARCHAR(20)  NOT NULL COMMENT '调用来源：REMOTE / MOCK',
    `model`       VARCHAR(100) NULL COMMENT '模型名',
    `task_code`   VARCHAR(64)  NULL COMMENT '任务编码',
    `success`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功',
    `error_type`  VARCHAR(50)  NULL COMMENT '失败类型：TIMEOUT / HTTP_ERROR / PARSE_ERROR / UNKNOWN',
    `message`     VARCHAR(500) NULL COMMENT '简要说明（不含密钥）',
    `duration_ms` INT          NULL COMMENT '耗时（毫秒）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_success` (`success`),
    KEY `idx_log_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='AI 调用日志（供后台查看失败记录）';

CREATE TABLE IF NOT EXISTS `interview_question` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`       VARCHAR(64)  NOT NULL COMMENT '题目编码，如 BG-JC-001',
    `category`   VARCHAR(32)  NOT NULL COMMENT '类目编码（与 task.category 同一套）',
    `title`      VARCHAR(300) NOT NULL COMMENT '问题',
    `frequency`  VARCHAR(10)  NOT NULL DEFAULT 'MID' COMMENT '考察频率：HIGH / MID / LOW',
    `difficulty` VARCHAR(16)  NOT NULL DEFAULT 'EASY' COMMENT '难度：EASY / MEDIUM / HARD / EXPERT',
    `tags`       VARCHAR(255) NULL COMMENT '标签，逗号分隔',
    `one_liner`  VARCHAR(500) NOT NULL COMMENT '一句话总结（快速背诵）',
    `plain`      TEXT         NOT NULL COMMENT '大白话解释',
    `points`     TEXT         NOT NULL COMMENT '背诵要点，JSON 字符串数组',
    `example`    TEXT         NULL COMMENT '示例（代码或场景）',
    `follow_ups` TEXT         NULL COMMENT '常见追问，JSON 字符串数组',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bagu_code` (`code`),
    KEY `idx_bagu_category` (`category`),
    KEY `idx_bagu_freq` (`frequency`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='八股复习题库';
