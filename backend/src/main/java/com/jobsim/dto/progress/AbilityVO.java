package com.jobsim.dto.progress;

/** 能力维度统计（雷达图数据源），工作台与能力分析页共用 */
public record AbilityVO(String dimension, double avgScore, int practiceCount, double maxScore) {
}
