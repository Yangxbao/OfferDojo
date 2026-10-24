package com.jobsim.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量 schema 迁移：为"已经初始化过的老库"补齐新增列。
 * 新库由 schema-*.sql 直接建出完整结构；老库走这里的 ALTER（列已存在时静默跳过）。
 * 这是学习项目务实的迁移方式，正式项目应使用 Flyway/Liquibase（见 ROADMAP）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrate("ALTER TABLE task_evaluation ADD COLUMN prompt_version VARCHAR(20) NULL");
        migrate("ALTER TABLE task_evaluation ADD COLUMN raw_response TEXT NULL");
        migrate("ALTER TABLE interview_question ADD COLUMN version_note VARCHAR(200) NULL");
    }

    private void migrate(String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.info("schema 迁移完成: {}", ddl);
        } catch (DataAccessException e) {
            // 列已存在是最常见的"失败"，属于预期；其余异常也要保证不阻断启动
            log.debug("schema 迁移跳过（可能已存在）: {} - {}", ddl, e.getMessage());
        }
    }
}
