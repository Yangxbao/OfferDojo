package com.jobsim.service.ai;

import com.jobsim.dto.task.RubricItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock AI 评估的单元测试：确定性评分逻辑（关键词覆盖率驱动）。
 */
class MockAiProviderTest {

    private static final List<RubricItem> RUBRIC = List.of(
            new RubricItem("需求理解", 50, "拆解签到、补签、记录查询三个能力"),
            new RubricItem("方案设计", 50, "REST 风格、接口完整"));

    private static final MockHints HINTS = new MockHints(
            Map.of("需求理解", List.of("签到", "截止", "补签", "记录"),
                    "方案设计", List.of("POST", "GET", "资源", "状态机")),
            List.of(new MockHints.StrengthHint("明确说明了补签接口由老师调用", List.of("补签")),
                    new MockHints.StrengthHint("使用了资源化路径设计", List.of("POST", "GET"))),
            List.of(new MockHints.ProblemHint("需求理解", "遗漏了手动补签接口")),
            List.of("重复签到如何处理？"),
            List.of("签到截止为什么用服务端时间判断？"),
            List.of("DB-001"));

    private final MockAiProvider provider = new MockAiProvider();

    private EvaluationContext context(String answer) {
        return new EvaluationContext("API-001", "课程签到 API", "背景", List.of("需求1"),
                List.of("约束1"), List.of("交付1"), List.of("验收1"),
                RUBRIC, answer, List.of("API-001", "DB-001"), HINTS);
    }

    @Test
    @DisplayName("覆盖要点的答案得分高于空泛答案")
    void richAnswerScoresHigherThanPoorAnswer() {
        String rich = """
                我会设计如下接口：
                1. POST /api/courses/{id}/sign-ins 老师发起签到，设置截止时间
                2. POST /api/sign-ins/{id}/check-in 学生签到
                3. PUT /api/sign-ins/{id}/late-check-in 老师补签
                4. GET /api/me/courses/{id}/sign-ins 学生查询签到记录
                状态机：正常/迟到/缺勤/已补签，截止后签到返回 400。
                """;
        EvaluationResult richResult = provider.evaluate(context(rich));
        EvaluationResult poorResult = provider.evaluate(context("我觉得设计几个接口就行了"));

        double richTotal = richResult.dimensionScores().stream().mapToDouble(d -> d.score() * d.weight()).sum();
        double poorTotal = poorResult.dimensionScores().stream().mapToDouble(d -> d.score() * d.weight()).sum();
        assertThat(richTotal).isGreaterThan(poorTotal);
    }

    @Test
    @DisplayName("评估结果是结构化数据：维度齐全、分数在 0-10 之间")
    void resultIsWellFormed() {
        EvaluationResult result = provider.evaluate(context("签到 POST GET 资源 状态机 补签 截止 记录"));
        assertThat(result.dimensionScores()).hasSameSizeAs(RUBRIC);
        for (var dim : result.dimensionScores()) {
            assertThat(dim.score()).isBetween(0.0, 10.0);
            assertThat(dim.weight()).isEqualTo(50);
            assertThat(dim.comment()).isNotBlank();
        }
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.edgeCases()).isNotEmpty();
        assertThat(result.followUps()).isNotEmpty();
    }

    @Test
    @DisplayName("低分维度会出现在问题列表中")
    void lowScoreDimensionsAppearInProblems() {
        EvaluationResult result = provider.evaluate(context("就写个接口"));
        assertThat(result.problems()).isNotEmpty();
    }

    @Test
    @DisplayName("没有提示数据的任务走通用关键词路径，不抛异常")
    void genericPathWithoutHints() {
        EvaluationContext ctx = new EvaluationContext("X-001", "t", "bg", List.of("r"), List.of("c"),
                List.of("d"), List.of("a"), RUBRIC, "方案 设计 接口 流程 边界 总结",
                List.of("X-001"), null);
        EvaluationResult result = provider.evaluate(ctx);
        assertThat(result.dimensionScores()).hasSameSizeAs(RUBRIC);
        // 通用路径的边界情况来自内置通用列表
        assertThat(result.edgeCases()).isNotEmpty();
        assertThat(result.edgeCases().get(0)).contains("并发");
    }
}
