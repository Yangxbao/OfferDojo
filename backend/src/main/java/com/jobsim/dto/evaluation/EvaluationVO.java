package com.jobsim.dto.evaluation;

import com.jobsim.dto.task.TaskVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估报告 VO：全结构化字段，前端按区块渲染。
 * nextTasks 已由后端解析为任务卡片数据，前端直接渲染链接。
 */
public record EvaluationVO(
        Long id,
        double totalScore,
        String provider,
        String model,
        String notice,
        LocalDateTime createdAt,
        List<DimensionScoreVO> dimensionScores,
        List<String> strengths,
        List<String> problems,
        List<String> edgeCases,
        List<String> suggestions,
        List<String> followUps,
        List<TaskVO> nextTasks
) {
}
