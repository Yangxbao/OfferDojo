package com.jobsim.dto.progress;

import com.jobsim.dto.task.TaskVO;

import java.util.List;

/**
 * 能力分析页数据：评分趋势 + 能力维度 + 薄弱环节 + 建议任务 + 完成率。
 * 统计口径见 docs/DATABASE.md；建议由规则基于真实评估数据生成。
 */
public record ProgressVO(
        List<TrendPointVO> trend,
        List<AbilityVO> abilities,
        List<WeaknessVO> weaknesses,
        List<TaskVO> suggestions,
        CompletionVO completion
) {

    public record TrendPointVO(String date, double avgScore, int count) {
    }

    public record WeaknessVO(String dimension, double avgScore, int practiceCount, String advice) {
    }

    public record CategoryStatVO(String category, String label, long published, long completed) {
    }

    public record CompletionVO(long completedTasks, long publishedTasks, double rate, List<CategoryStatVO> categories) {
    }
}
