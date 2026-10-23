package com.jobsim.dto.task;

import java.util.List;

/**
 * 任务列表项 / 简要卡片。
 * myStatus：NOT_STARTED 未开始 / DRAFTING 草稿中 / COMPLETED 已完成（基于当前用户数据计算）
 */
public record TaskVO(
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
        Double myBestScore
) {
}
