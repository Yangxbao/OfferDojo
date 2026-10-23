package com.jobsim.dto.submission;

import java.time.LocalDateTime;

/** 练习历史列表项 */
public record SubmissionVO(
        Long id,
        Long taskId,
        String taskTitle,
        String taskCategory,
        String taskDifficulty,
        String status,
        Double totalScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
