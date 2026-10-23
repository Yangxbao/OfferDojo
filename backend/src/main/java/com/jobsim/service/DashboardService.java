package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobsim.dto.dashboard.DashboardVO;
import com.jobsim.dto.progress.AbilityVO;
import com.jobsim.dto.submission.SubmissionVO;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作台服务：今日任务、累计完成、能力维度、最近练习、推荐内容、继续上次任务。
 * 所有数字都来自当前用户的真实提交/评估记录。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskService taskService;
    private final SubmissionMapper submissionMapper;
    private final EvaluationMapper evaluationMapper;
    private final TaskMapper taskMapper;

    public DashboardVO dashboard(Long userId) {
        List<Task> published = taskService.listPublished();
        Set<Long> completedIds = taskService.completedTaskIds(userId);
        List<Evaluation> recentEvaluations = evaluationMapper.selectList(
                Wrappers.<Evaluation>lambdaQuery()
                        .eq(Evaluation::getUserId, userId)
                        .orderByDesc(Evaluation::getCreatedAt)
                        .last("LIMIT 100"));
        List<AbilityVO> abilities = AbilityCalculator.aggregate(recentEvaluations);

        // ---- 今日实战任务：从未完成的任务中按日期轮换挑选，同一天所有人看到同一道 ----
        List<Task> todo = published.stream().filter(t -> !completedIds.contains(t.getId())).toList();
        Task today = null;
        if (!todo.isEmpty()) {
            long day = LocalDate.now().toEpochDay();
            today = todo.get((int) (day % todo.size()));
        } else if (!published.isEmpty()) {
            today = published.get(0);
        }
        TaskVO todayVO = today == null ? null : taskService.toTaskVO(today, userId);
        String todayReason = today == null ? "任务库为空，请联系管理员"
                : completedIds.contains(today.getId())
                ? "你已完成全部内置任务，可以选择任意任务复盘重练"
                : "根据练习进度轮换推荐，建议在 %d 分钟内专注完成".formatted(today.getEstimatedMinutes());

        // ---- 最近练习：取 5 条已评估记录（searchCount=false 免掉多余的 count 查询） ----
        IPage<SubmissionMapper.SubmissionRow> recentPage =
                submissionMapper.selectMyHistory(new Page<>(1, 5, false), userId);
        List<SubmissionVO> recent = recentPage.getRecords().stream()
                .map(r -> new SubmissionVO(r.id(), r.taskId(), r.taskTitle(), r.taskCategory(),
                        r.taskDifficulty(), r.status(),
                        r.totalScore() == null ? null : r.totalScore().doubleValue(),
                        r.createdAt(), r.updatedAt()))
                .toList();

        // ---- 推荐练习：优先匹配薄弱维度权重高的任务 ----
        List<TaskVO> recommended = recommendByWeakness(userId, abilities, published, completedIds);

        // ---- 继续上次任务：最近一份草稿 ----
        Submission draft = submissionMapper.selectOne(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getUserId, userId)
                .eq(Submission::getStatus, Submission.STATUS_DRAFT)
                .orderByDesc(Submission::getUpdatedAt)
                .last("LIMIT 1"));
        DashboardVO.ResumeVO resume = null;
        if (draft != null) {
            Task draftTask = taskMapper.selectById(draft.getTaskId());
            if (draftTask != null) {
                resume = new DashboardVO.ResumeVO(draft.getId(), draftTask.getId(),
                        draftTask.getTitle(), draft.getUpdatedAt());
            }
        }

        return new DashboardVO(todayVO, todayReason, completedIds.size(), published.size(),
                abilities, recent, recommended, resume);
    }

    /** 按薄弱维度推荐：任务的评分标准里命中薄弱维度权重越高的任务越靠前 */
    private List<TaskVO> recommendByWeakness(Long userId, List<AbilityVO> abilities,
                                             List<Task> published, Set<Long> completedIds) {
        // 练习次数 >= 1 且平均分最低的两个维度视为薄弱维度
        List<AbilityVO> weak = abilities.stream()
                .filter(a -> a.practiceCount() > 0 && a.avgScore() < 7)
                .sorted(Comparator.comparingDouble(AbilityVO::avgScore))
                .limit(2)
                .toList();

        Map<Long, Double> weakWeightByTask = new HashMap<>();
        if (!weak.isEmpty()) {
            for (Task t : published) {
                double weightSum = 0;
                for (RubricItem item : JsonUtils.readList(t.getRubricJson(), RubricItem.class)) {
                    for (AbilityVO w : weak) {
                        if (w.dimension().equals(item.dimension())) {
                            weightSum += item.weight();
                        }
                    }
                }
                weakWeightByTask.put(t.getId(), weightSum);
            }
        }

        List<Task> candidates = published.stream()
                .filter(t -> !completedIds.contains(t.getId()))
                .toList();
        List<Task> ordered = new ArrayList<>(candidates);
        if (!weak.isEmpty()) {
            ordered.sort(Comparator.comparingDouble((Task t) -> weakWeightByTask.getOrDefault(t.getId(), 0.0))
                    .reversed());
        } else {
            ordered.sort(Comparator.comparingInt(TaskService::difficultyRank));
        }

        List<TaskVO> result = new ArrayList<>();
        for (Task t : ordered) {
            if (result.size() >= 3) {
                break;
            }
            result.add(taskService.toTaskVO(t, userId));
        }
        return result;
    }
}
