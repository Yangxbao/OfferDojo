package com.jobsim.dto.learning;

import java.time.LocalDateTime;

/** 个人学习计划条目 VO */
public record PlanItemVO(
        Long id,
        String itemType,
        String itemCode,
        String itemTitle,
        String status,
        String note,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {
}
