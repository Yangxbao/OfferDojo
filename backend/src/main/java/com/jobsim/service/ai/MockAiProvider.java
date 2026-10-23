package com.jobsim.service.ai;

import com.jobsim.dto.task.RubricItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 内置模拟 AI 评估实现（默认启用，无需任何 API Key）。
 *
 * 评分思路（确定性，可复现，方便单元测试）：
 * 1. 对每个评分维度，检查答案命中了预设关键词中的多少个，得到"要点覆盖率"；
 * 2. 分数 = 基础分 + 覆盖率加成 + 篇幅加成，收敛到 [3, 10]；
 * 3. 亮点/问题/边界情况/建议都从任务的预设内容（mockHints）中按命中情况挑选；
 *    管理员手工创建的任务没有提示数据时，退化为各维度的通用关键词与通用建议。
 */
public class MockAiProvider implements AiProvider {

    public static final String NAME = "MOCK";

    /** 任务没有提示数据时使用的各维度通用关键词 */
    private static final Map<String, List<String>> GENERIC_KEYWORDS = Map.of(
            "需求理解", List.of("需求", "场景", "角色", "边界", "目标", "功能"),
            "方案设计", List.of("方案", "设计", "结构", "接口", "流程", "选型"),
            "问题定位", List.of("原因", "定位", "排查", "日志", "复现", "证据"),
            "正确性", List.of("校验", "异常", "边界", "并发", "事务", "索引"),
            "工程规范", List.of("规范", "命名", "测试", "注释", "上限", "约束"),
            "沟通表达", List.of("总结", "结构", "首先", "其次", "结论", "层次"));

    private static final List<String> GENERIC_EDGE_CASES = List.of(
            "并发/重复操作下，你的方案是否仍然正确？",
            "依赖的服务或数据不可用时，表现是什么？",
            "输入处于边界值时，行为是否符合预期？");

    private static final List<String> GENERIC_FOLLOW_UPS = List.of(
            "请举例说明你的方案在一个典型边界场景下的表现。",
            "如果数据量或访问量增长 100 倍，你的方案需要做哪些调整？");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext ctx) {
        String answer = ctx.answer() == null ? "" : ctx.answer();
        String lower = answer.toLowerCase();
        MockHints hints = ctx.mockHints();

        // ---- 1. 各维度评分：关键词覆盖率决定分数 ----
        List<EvaluationResult.DimensionScore> dims = new ArrayList<>();
        for (RubricItem item : ctx.rubric()) {
            List<String> keywords = keywordsFor(hints, item.dimension());
            long matched = keywords.stream().filter(k -> lower.contains(k.toLowerCase())).count();
            double coverage = keywords.isEmpty() ? 0.5 : (double) matched / keywords.size();
            double score = 4 + coverage * 5;
            if (answer.length() >= 300) {
                score += 1; // 展开充分
            }
            if (answer.length() < 80) {
                score -= 0.5; // 过短，提示补充
            }
            score = Math.round(Math.min(10, Math.max(3, score)) * 10) / 10.0;
            dims.add(new EvaluationResult.DimensionScore(
                    item.dimension(), score, item.weight(),
                    comment(score, matched, keywords.size(), item.guidance())));
        }

        // ---- 2. 结构化反馈 ----
        List<String> strengths = pickStrengths(hints, dims, lower);
        List<String> problems = pickProblems(hints, dims);
        List<String> edgeCases = hints == null || hints.edgeCases() == null
                ? GENERIC_EDGE_CASES : hints.edgeCases().stream().limit(3).toList();
        List<String> suggestions = pickSuggestions(ctx.rubric(), dims, answer.length());
        List<String> followUps = hints == null || hints.followUps() == null
                ? GENERIC_FOLLOW_UPS : hints.followUps().stream().limit(3).toList();
        List<String> nextTaskCodes = hints == null || hints.nextTaskCodes() == null
                ? List.of() : hints.nextTaskCodes();

        return new EvaluationResult(dims, strengths, problems, edgeCases, suggestions, followUps, nextTaskCodes);
    }

    private List<String> keywordsFor(MockHints hints, String dimension) {
        if (hints != null && hints.keywords() != null && hints.keywords().containsKey(dimension)) {
            return hints.keywords().get(dimension);
        }
        return GENERIC_KEYWORDS.getOrDefault(dimension, List.of("关键词"));
    }

    private String comment(double score, long matched, int total, String guidance) {
        if (score >= 8) {
            return "覆盖较全面（命中要点 %d/%d），展开充分，继续保持".formatted(matched, total);
        }
        if (score >= 5.5) {
            return "基本覆盖（命中要点 %d/%d），建议补充更具体的方案细节与取舍依据".formatted(matched, total);
        }
        return "证据不足（命中要点 %d/%d），参考评分指引：%s".formatted(matched, total, firstSentence(guidance));
    }

    private List<String> pickStrengths(MockHints hints, List<EvaluationResult.DimensionScore> dims, String lower) {
        List<String> result = new ArrayList<>();
        if (hints != null && hints.strengths() != null) {
            for (MockHints.StrengthHint hint : hints.strengths()) {
                if (hint.keywords() == null || hint.keywords().isEmpty()
                        || hint.keywords().stream().anyMatch(k -> lower.contains(k.toLowerCase()))) {
                    result.add(hint.text());
                }
                if (result.size() >= 3) {
                    break;
                }
            }
        }
        if (result.isEmpty() && !dims.isEmpty()) {
            EvaluationResult.DimensionScore best = dims.stream()
                    .max(Comparator.comparingDouble(EvaluationResult.DimensionScore::score)).orElseThrow();
            result.add("在「%s」维度提供了较多有效信息（%.1f 分）".formatted(best.dimension(), best.score()));
        }
        return result;
    }

    private List<String> pickProblems(MockHints hints, List<EvaluationResult.DimensionScore> dims) {
        List<String> result = new ArrayList<>();
        for (EvaluationResult.DimensionScore dim : dims) {
            if (dim.score() >= 6.5) {
                continue;
            }
            boolean added = false;
            if (hints != null && hints.problems() != null) {
                for (MockHints.ProblemHint hint : hints.problems()) {
                    if (dim.dimension().equals(hint.dimension())) {
                        result.add(hint.text());
                        added = true;
                        break;
                    }
                }
            }
            if (!added) {
                result.add("「%s」维度得分 %.1f，建议对照评分指引补充该维度的具体内容".formatted(dim.dimension(), dim.score()));
            }
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private List<String> pickSuggestions(List<RubricItem> rubric, List<EvaluationResult.DimensionScore> dims,
                                         int answerLength) {
        List<String> result = new ArrayList<>();
        for (EvaluationResult.DimensionScore dim : dims) {
            if (dim.score() >= 7) {
                continue;
            }
            rubric.stream()
                    .filter(r -> r.dimension().equals(dim.dimension()))
                    .findFirst()
                    .ifPresent(r -> result.add("强化「%s」：%s".formatted(dim.dimension(), firstSentence(r.guidance()))));
            if (result.size() >= 3) {
                break;
            }
        }
        if (answerLength < 200 && result.size() < 4) {
            result.add("答案篇幅较短，建议补充具体的接口定义、SQL 或伪代码来增强说服力");
        }
        return result;
    }

    private String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String sep : new String[]{"。", "；", ";"}) {
            int idx = text.indexOf(sep);
            if (idx > 0) {
                return text.substring(0, idx);
            }
        }
        return text.length() > 50 ? text.substring(0, 50) : text;
    }
}
