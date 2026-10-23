package com.jobsim.dto.evaluation;

/** 单个能力维度的得分与评语 */
public record DimensionScoreVO(String dimension, double score, int weight, String comment) {
}
