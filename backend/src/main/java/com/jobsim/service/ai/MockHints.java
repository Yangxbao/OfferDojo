package com.jobsim.service.ai;

import java.util.List;
import java.util.Map;

/**
 * 模拟评估的提示数据（与任务一起存库，字段 mock_hints_json）。
 * 关键词列表用于对答案做"要点覆盖率"打分，让 Mock 评估有理有据、结果可复现。
 */
public record MockHints(
        Map<String, List<String>> keywords,
        List<StrengthHint> strengths,
        List<ProblemHint> problems,
        List<String> edgeCases,
        List<String> followUps,
        List<String> nextTaskCodes
) {

    public record StrengthHint(String text, List<String> keywords) {
    }

    public record ProblemHint(String dimension, String text) {
    }
}
