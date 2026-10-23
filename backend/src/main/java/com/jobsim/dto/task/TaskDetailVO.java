package com.jobsim.dto.task;

import java.util.List;

/**
 * 任务详情：完整任务内容 + 当前用户在此任务上的草稿与练习状态。
 */
public record TaskDetailVO(
        Long id,
        String code,
        String title,
        String category,
        String categoryLabel,
        String difficulty,
        List<String> tags,
        String summary,
        Integer estimatedMinutes,
        String status,
        String myStatus,
        Double myBestScore,
        String background,
        List<String> requirements,
        List<String> constraintItems,
        List<String> deliverables,
        List<String> acceptance,
        List<RubricItem> rubric,
        Long myDraftId,
        String myDraftContent,
        Long myLatestSubmissionId
) {
}
