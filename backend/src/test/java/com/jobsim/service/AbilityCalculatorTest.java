package com.jobsim.service;

import com.jobsim.dto.progress.AbilityVO;
import com.jobsim.entity.Evaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力维度聚合的单元测试（纯函数）。
 */
class AbilityCalculatorTest {

    private Evaluation evaluation(String dimensionScoresJson) {
        Evaluation e = new Evaluation();
        e.setDimensionScores(dimensionScoresJson);
        return e;
    }

    @Test
    @DisplayName("按维度聚合平均值与练习次数")
    void aggregateByDimension() {
        List<Evaluation> evaluations = List.of(
                evaluation("""
                        [{"dimension":"需求理解","score":8.0,"weight":50,"comment":"ok"},
                         {"dimension":"正确性","score":6.0,"weight":50,"comment":"ok"}]"""),
                evaluation("""
                        [{"dimension":"需求理解","score":6.0,"weight":50,"comment":"ok"},
                         {"dimension":"正确性","score":8.0,"weight":50,"comment":"ok"}]"""));

        List<AbilityVO> abilities = AbilityCalculator.aggregate(evaluations);

        assertThat(abilities).hasSize(6);
        AbilityVO req = abilities.stream().filter(a -> a.dimension().equals("需求理解")).findFirst().orElseThrow();
        assertThat(req.avgScore()).isEqualTo(7.0);
        assertThat(req.practiceCount()).isEqualTo(2);
        AbilityVO correct = abilities.stream().filter(a -> a.dimension().equals("正确性")).findFirst().orElseThrow();
        assertThat(correct.avgScore()).isEqualTo(7.0);
        // 没有练习记录的维度计数为 0，均分 0（雷达图展示占位）
        AbilityVO design = abilities.stream().filter(a -> a.dimension().equals("方案设计")).findFirst().orElseThrow();
        assertThat(design.practiceCount()).isZero();
        assertThat(design.avgScore()).isZero();
    }

    @Test
    @DisplayName("空的评估列表返回六个零值维度而不是报错")
    void emptyEvaluations() {
        List<AbilityVO> abilities = AbilityCalculator.aggregate(List.of());
        assertThat(abilities).hasSize(6);
        assertThat(abilities).allSatisfy(a -> {
            assertThat(a.avgScore()).isZero();
            assertThat(a.practiceCount()).isZero();
        });
    }
}
