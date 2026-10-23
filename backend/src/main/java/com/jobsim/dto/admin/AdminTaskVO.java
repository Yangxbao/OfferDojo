package com.jobsim.dto.admin;

import java.time.LocalDateTime;

/** 后台任务管理列表项：任务信息 + 练习统计 */
public record AdminTaskVO(
        Long id,
        String code,
        String title,
        String category,
        String categoryLabel,
        String difficulty,
        String tags,
        String summary,
        Integer estimatedMinutes,
        String status,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long attempts,
        Long userCount,
        Double avgScore
) {
}
