package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.dto.progress.AbilityVO;
import com.jobsim.dto.progress.ProgressVO;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.CategoryLabels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力分析服务：评分趋势、能力维度、薄弱环节、建议任务、完成率。
 * 统计全部基于 task_evaluation / task_submission / task 三张表的真实数据实时计算。
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    /** 薄弱维度的固定学习建议（规则生成，非 AI 生成，避免夸大能力） */
    private static final Map<String, String> DIMENSION_ADVICE = Map.of(
            "需求理解", "先复述需求再动手：用 3-5 条列出功能点与边界条件，推荐从 API 设计类任务开始。",
            "方案设计", "先画结构再写细节：接口定义/表结构/流程说明能显著提升方案质量。",
            "问题定位", "按「证据 → 假设 → 验证」组织排查过程，把日志和 EXPLAIN 当成证据引用。",
            "正确性", "关注并发、幂等、边界值三个高频失分点，提交前自查一遍。",
            "工程规范", "补充索引、命名、参数上限等工程细节，让方案能直接进入代码评审。",
            "沟通表达", "用「现象-分析-方案-验证」的结构组织答案，先结论后论据。");

    private final EvaluationMapper evaluationMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    public ProgressVO progress(Long userId) {
        // ---- 评分趋势（近 30 天按天平均） ----
        List<ProgressVO.TrendPointVO> trend = new ArrayList<>();
        for (var row : evaluationMapper.selectTrend(userId, LocalDateTime.now().minusDays(30))) {
            trend.add(new ProgressVO.TrendPointVO(row.date().toString(),
                    row.avgScore() == null ? 0 : row.avgScore().doubleValue(), row.cnt()));
        }

        // ---- 能力维度 ----
        List<Evaluation> evaluations = evaluationMapper.selectList(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getUserId, userId)
                .orderByDesc(Evaluation::getCreatedAt)
                .last("LIMIT 100"));
        List<AbilityVO> abilities = AbilityCalculator.aggregate(evaluations);

        // ---- 薄弱环节：平均分低于 7 的维度，从低到高取 3 个 ----
        List<ProgressVO.WeaknessVO> weaknesses = new ArrayList<>();
        abilities.stream()
                .filter(a -> a.practiceCount() > 0 && a.avgScore() < 7)
                .sorted(Comparator.comparingDouble(AbilityVO::avgScore))
                .limit(3)
                .forEach(a -> weaknesses.add(new ProgressVO.WeaknessVO(a.dimension(), a.avgScore(),
                        a.practiceCount(), DIMENSION_ADVICE.getOrDefault(a.dimension(),
                        "针对该维度多做对应类型任务，并对照评分指引复盘。"))));

        // ---- 建议任务：与薄弱维度匹配 ----
        Set<Long> completedIds = taskService.completedTaskIds(userId);
        List<TaskVO> suggestions = recommend(userId, abilities, completedIds);

        // ---- 完成率：按类目对比已发布与已完成 ----
        Map<String, Long> publishedByCategory = new HashMap<>();
        for (var row : taskMapper.selectPublishedCountByCategory()) {
            publishedByCategory.put(row.category(), row.total());
        }
        Map<String, Long> completedByCategory = new HashMap<>();
        for (var row : submissionMapper.selectCompletedByCategory(userId)) {
            completedByCategory.put(row.category(), row.completed());
        }
        long publishedTotal = 0;
        long completedTotal = submissionMapper.countDistinctCompletedTasks(userId);
        List<ProgressVO.CategoryStatVO> categories = new ArrayList<>();
        for (var entry : publishedByCategory.entrySet()) {
            publishedTotal += entry.getValue();
            long completed = completedByCategory.getOrDefault(entry.getKey(), 0L);
            categories.add(new ProgressVO.CategoryStatVO(entry.getKey(),
                    CategoryLabels.label(entry.getKey()), entry.getValue(), completed));
        }
        categories.sort(Comparator.comparing(ProgressVO.CategoryStatVO::category));
        double rate = publishedTotal == 0 ? 0 : Math.round(completedTotal * 1000.0 / publishedTotal) / 10.0;

        return new ProgressVO(trend, abilities, weaknesses, suggestions,
                new ProgressVO.CompletionVO(completedTotal, publishedTotal, rate, categories));
    }

    private List<TaskVO> recommend(Long userId, List<AbilityVO> abilities, Set<Long> completedIds) {
        List<AbilityVO> weak = abilities.stream()
                .filter(a -> a.practiceCount() > 0 && a.avgScore() < 7)
                .sorted(Comparator.comparingDouble(AbilityVO::avgScore))
                .limit(2)
                .toList();
        List<Task> published = taskService.listPublished();

        List<TaskVO> result = new ArrayList<>();
        for (Task t : published) {
            if (completedIds.contains(t.getId()) || result.size() >= 3) {
                continue;
            }
            if (weak.isEmpty()) {
                result.add(taskService.toTaskVO(t, userId));
                continue;
            }
            boolean matchesWeak = false;
            for (RubricItem item : com.jobsim.util.JsonUtils.readList(t.getRubricJson(), RubricItem.class)) {
                for (AbilityVO w : weak) {
                    if (w.dimension().equals(item.dimension())) {
                        matchesWeak = true;
                    }
                }
            }
            if (matchesWeak) {
                result.add(taskService.toTaskVO(t, userId));
            }
        }
        // 薄弱维度匹配不足 3 个时补充未完成任务
        if (result.size() < 3) {
            for (Task t : published) {
                if (result.size() >= 3) {
                    break;
                }
                if (!completedIds.contains(t.getId())
                        && result.stream().noneMatch(v -> v.id().equals(t.getId()))) {
                    result.add(taskService.toTaskVO(t, userId));
                }
            }
        }
        return result;
    }
}
