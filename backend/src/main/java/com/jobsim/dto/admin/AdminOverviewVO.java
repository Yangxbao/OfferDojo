package com.jobsim.dto.admin;

/** 后台总览统计 */
public record AdminOverviewVO(
        long userCount,
        long publishedTasks,
        long totalTasks,
        long submissionCount,
        long evaluationCount,
        double avgScore,
        long failureCount24h
) {
}
