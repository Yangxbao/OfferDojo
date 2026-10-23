package com.jobsim.dto.submission;

import com.jobsim.dto.evaluation.EvaluationVO;

import java.time.LocalDateTime;

/** 提交详情：答案内容 + 评估报告（草稿时 evaluation 为 null） */
public record SubmissionDetailVO(
        Long id,
        Long taskId,
        String taskTitle,
        String taskCategory,
        String taskDifficulty,
        String status,
        String content,
        LocalDateTime updatedAt,
        EvaluationVO evaluation
) {
}
