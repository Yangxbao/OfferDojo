package com.jobsim.service;

import com.jobsim.dto.progress.AbilityVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力维度聚合器：把评估记录中的维度分数聚合为雷达图数据。
 * 全系统固定的六个能力维度；每个维度取历次得分的平均值。
 * 纯函数设计（无数据库依赖），方便单元测试。
 */
public class AbilityCalculator {

    public static final List<String> DIMENSIONS =
            List.of("需求理解", "方案设计", "问题定位", "正确性", "工程规范", "沟通表达");

    private AbilityCalculator() {
    }

    public static List<AbilityVO> aggregate(List<Evaluation> evaluations) {
        Map<String, List<Double>> byDim = new HashMap<>();
        for (Evaluation evaluation : evaluations) {
            for (DimScore ds : JsonUtils.readList(evaluation.getDimensionScores(), DimScore.class)) {
                if (ds.dimension() != null && ds.score() > 0) {
                    byDim.computeIfAbsent(ds.dimension(), k -> new ArrayList<>()).add(ds.score());
                }
            }
        }
        List<AbilityVO> result = new ArrayList<>();
        for (String dim : DIMENSIONS) {
            List<Double> scores = byDim.getOrDefault(dim, List.of());
            double avg = scores.isEmpty() ? 0
                    : Math.round(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
            result.add(new AbilityVO(dim, avg, scores.size(), 10));
        }
        return result;
    }

    /** 评估 JSON 中的维度得分条目 */
    public record DimScore(String dimension, double score, Integer weight, String comment) {
    }
}
