package com.jobsim.service.ai;

import java.util.List;

/**
 * 评估结果（结构化数据，不是一整段模型回复）。
 * Mock 与 Remote 两个 Provider 都产出这个结构，上层存储与展示逻辑完全一致。
 *
 * @param dimensionScores 各维度得分（score 0-10）
 * @param nextTaskCodes   建议下一步练习的任务编码（服务层会校验合法性）
 * @param rawText         原始返回：Remote 为模型原文（可追溯），Mock 为评分明细 JSON
 */
public record EvaluationResult(
        List<DimensionScore> dimensionScores,
        List<String> strengths,
        List<String> problems,
        List<String> edgeCases,
        List<String> suggestions,
        List<String> followUps,
        List<String> nextTaskCodes,
        String rawText
) {

    public record DimensionScore(String dimension, double score, int weight, String comment) {
    }
}
